package com.kintil.doommod.util;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

@SideOnly(Side.CLIENT)
public class DoomEngine {

    private Process        doomProcess;
    private File           frameBufferFile;
    private File           tempDir;
    private int            width, height;
    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicBoolean initDone   = new AtomicBoolean(false);
    private byte[]         frameBytes;
    private OutputStream   doomStdin;
    private RandomAccessFile rafFramebuffer;

    // ── Progress / log callback ───────────────────────────────────────────────
    // Called from the background init thread with (progressPercent 0‥100, message).
    // Set this before calling initAsync().
    private volatile BiConsumer<Integer, String> progressCallback = null;

    // ── Doom key codes (doom-generic protocol) ────────────────────────────────
    public static final int DOOM_KEY_RIGHT    = 0xae;
    public static final int DOOM_KEY_LEFT     = 0xac;
    public static final int DOOM_KEY_UP       = 0xad;
    public static final int DOOM_KEY_DOWN     = 0xaf;
    public static final int DOOM_KEY_STRAFE_L = 0xa0;
    public static final int DOOM_KEY_STRAFE_R = 0xa1;
    public static final int DOOM_KEY_FIRE     = 0xa3;
    public static final int DOOM_KEY_USE      = 0xa4;
    public static final int DOOM_KEY_ENTER    = 13;
    public static final int DOOM_KEY_ESCAPE   = 27;

    /** Set a callback (progress 0‥100, message) fired during async init. */
    public void setProgressCallback(BiConsumer<Integer, String> cb) {
        this.progressCallback = cb;
    }

    private void progress(int pct, String msg) {
        System.out.println("[DoomMod] [" + pct + "%] " + msg);
        BiConsumer<Integer, String> cb = progressCallback;
        if (cb != null) cb.accept(pct, msg);
    }

    /** Returns true immediately. Fires progressCallback on a daemon thread.
     *  When done: progress(100, "Ready") if success, progress(-1, error) on failure. */
    public void initAsync(String wadPath, int width, int height) {
        this.width      = width;
        this.height     = height;
        this.frameBytes = new byte[width * height * 4];

        Thread t = new Thread(() -> doInit(wadPath), "DoomMod-Init");
        t.setDaemon(true);
        t.start();
    }

    /** Synchronous init — kept for compatibility, blocks caller. Use initAsync() instead. */
    public boolean init(String wadPath, int width, int height) {
        this.width      = width;
        this.height     = height;
        this.frameBytes = new byte[width * height * 4];
        return doInit(wadPath);
    }

    /** Returns true once initAsync has finished successfully. */
    public boolean isReady() { return initDone.get() && running.get(); }

    private boolean doInit(String wadPath) {
        try {
            progress(5, "Creating temp directory...");
            tempDir         = Files.createTempDirectory("doommod").toFile();
            frameBufferFile = new File(tempDir, "framebuffer.raw");

            progress(15, "Extracting Doom binary...");
            File doomExe = extractDoomExecutable(tempDir);
            if (doomExe == null) {
                progress(-1, "No Doom binary for this platform.");
                return false;
            }
            System.out.println("[DoomMod] Binary: " + doomExe.getAbsolutePath()
                    + " (" + doomExe.length() + " bytes, exe=" + doomExe.canExecute() + ")");

            progress(30, "Checking WAD file...");
            File wad = new File(wadPath);
            if (!wad.exists()) {
                progress(-1, "WAD not found: " + wadPath);
                return false;
            }
            System.out.println("[DoomMod] WAD: " + wad.getAbsolutePath());
            System.out.println("[DoomMod] Framebuffer target: " + frameBufferFile.getAbsolutePath());
            progress(40, "WAD OK (" + (wad.length() / 1024) + " KB). Launching...");

            // Pre-create framebuffer file with correct size so doom can overwrite instead of create
            int fbSize = width * height * 4;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(frameBufferFile, "rw")) {
                raf.setLength(fbSize);
            }
            System.out.println("[DoomMod] Pre-created framebuffer file: " + fbSize + " bytes");

            List<String> cmd = new ArrayList<>();
            cmd.add(doomExe.getAbsolutePath());
            cmd.add("-iwad");        cmd.add(wad.getAbsolutePath());
            cmd.add("-framebuffer"); cmd.add(frameBufferFile.getAbsolutePath());
            cmd.add("-width");       cmd.add(String.valueOf(width));
            cmd.add("-height");      cmd.add(String.valueOf(height));
            cmd.add("-nomusic");
            cmd.add("-nosound");
            System.out.println("[DoomMod] CMD: " + cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tempDir);
            // Set DOOMMOD_FRAMEBUFFER env var (alternative protocol some builds use)
            pb.environment().put("DOOMMOD_FRAMEBUFFER", frameBufferFile.getAbsolutePath());
            // Merge stderr into stdout so we capture everything
            pb.redirectErrorStream(true);
            doomProcess = pb.start();
            doomStdin   = doomProcess.getOutputStream();
            running.set(true);

            // Buffer all Doom stdout/stderr for crash diagnosis
            final List<String> doomOutput = new ArrayList<>();
            Thread drain = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(doomProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[Doom] " + line);
                        synchronized (doomOutput) { doomOutput.add(line); }
                        BiConsumer<Integer, String> cb = progressCallback;
                        if (cb != null && !line.trim().isEmpty()) {
                            cb.accept(75, "\u00a77" + line.trim());
                        }
                    }
                } catch (IOException ignored) {}
            }, "DoomMod-Drain");
            drain.setDaemon(true);
            drain.start();

            // Wait briefly for instant-crash detection
            Thread.sleep(500);
            if (!doomProcess.isAlive()) {
                int code = doomProcess.exitValue();
                synchronized (doomOutput) {
                    for (String l : doomOutput) System.out.println("[Doom-crash] " + l);
                    String lastLine = doomOutput.isEmpty() ? "(no output)" : doomOutput.get(doomOutput.size() - 1);
                    progress(-1, "Doom exited (code " + code + "): " + lastLine);
                }
                return false;
            }

            progress(55, "Waiting for Doom to write framebuffer...");
            long deadline = System.currentTimeMillis() + 15000; // 15s for slow AV scan
            int waitPct   = 55;

            // Since we pre-created the file, check that Doom actually wrote real pixel data.
            // We detect this by watching file last-modified time change.
            long lastModified = frameBufferFile.lastModified();

            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
                if (!doomProcess.isAlive()) {
                    int code = doomProcess.exitValue();
                    // Wait a moment for drain thread to collect output
                    Thread.sleep(300);
                    synchronized (doomOutput) {
                        for (String l : doomOutput) System.out.println("[Doom-crash] " + l);
                        String lastLine = doomOutput.isEmpty() ? "(no output — possible AV block or missing DLL)" : doomOutput.get(doomOutput.size() - 1);
                        progress(-1, "Doom exited (code " + code + "): " + lastLine);
                    }
                    return false;
                }
                // Check if Doom has written real data (modified time changed)
                long newMod = frameBufferFile.lastModified();
                if (newMod != lastModified) {
                    System.out.println("[DoomMod] Framebuffer written! (mod time changed)");
                    break;
                }
                waitPct = Math.min(90, waitPct + 1);
                BiConsumer<Integer, String> cb = progressCallback;
                if (cb != null) cb.accept(waitPct, "Waiting for first frame...");
            }

            if (frameBufferFile.lastModified() == lastModified) {
                int code = doomProcess.isAlive() ? -999 : doomProcess.exitValue();
                progress(-1, "Doom running but not writing (exit=" + code + ") — check AV/Defender");
                return false;
            }

            progress(92, "Opening framebuffer...");
            rafFramebuffer = new RandomAccessFile(frameBufferFile, "r");
            initDone.set(true);
            progress(100, "Ready! RIP AND TEAR");
            return true;

        } catch (Exception e) {
            progress(-1, "Fatal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Called every client tick — reads latest framebuffer into pixelBuffer (ARGB int[]).
     */
    public void tick(int[] pixelBuffer) {
        if (!running.get()) return;

        if (doomProcess != null && !doomProcess.isAlive()) {
            running.set(false);
            return;
        }

        try {
            long fileLen = rafFramebuffer.length();
            if (fileLen < frameBytes.length) return; // not ready yet

            rafFramebuffer.seek(0);
            rafFramebuffer.readFully(frameBytes);

            // RGBA bytes → packed ARGB int
            for (int i = 0; i < width * height; i++) {
                int r = frameBytes[i * 4]     & 0xFF;
                int g = frameBytes[i * 4 + 1] & 0xFF;
                int b = frameBytes[i * 4 + 2] & 0xFF;
                // frameBytes[i*4+3] is alpha from doom (always 255), skip it
                pixelBuffer[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        } catch (IOException ignored) {}
    }

    /**
     * Send key event via stdin.
     * Protocol (doom-generic-minecraft): byte 'K', key code, 1=press / 0=release
     */
    public void sendKeyEvent(int doomKeyCode, boolean pressed) {
        if (!running.get() || doomStdin == null) return;
        try {
            doomStdin.write('K');
            doomStdin.write(doomKeyCode & 0xFF);
            doomStdin.write(pressed ? 1 : 0);
            doomStdin.flush();
        } catch (IOException ignored) {}
    }

    public void destroy() {
        running.set(false);
        try { if (rafFramebuffer != null) rafFramebuffer.close(); } catch (IOException ignored) {}
        if (doomProcess != null && doomProcess.isAlive()) doomProcess.destroy();
        if (tempDir != null && tempDir.exists()) deleteDir(tempDir);
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory())
            for (File f : dir.listFiles()) deleteDir(f);
        dir.delete();
    }

    private File extractDoomExecutable(File targetDir) {
        String os = System.getProperty("os.name").toLowerCase();
        String resourceName, outName;

        if (os.contains("win")) {
            resourceName = "/assets/doommod/bin/doom-windows-x64.exe";
            outName = "doom.exe";
        } else if (os.contains("mac")) {
            resourceName = "/assets/doommod/bin/doom-macos-x64";
            outName = "doom";
        } else {
            resourceName = "/assets/doommod/bin/doom-linux-x64";
            outName = "doom";
        }

        InputStream in = DoomEngine.class.getResourceAsStream(resourceName);
        if (in == null) {
            System.err.println("[DoomMod] Binary not in jar: " + resourceName);
            return null;
        }

        File out = new File(targetDir, outName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        } catch (IOException e) {
            System.err.println("[DoomMod] Failed to extract binary: " + e.getMessage());
            return null;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }

        if (!os.contains("win")) out.setExecutable(true);
        return out;
    }
}
