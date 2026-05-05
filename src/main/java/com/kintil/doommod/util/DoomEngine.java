package com.kintil.doommod.util;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SideOnly(Side.CLIENT)
public class DoomEngine {

    private Process        doomProcess;
    private File           frameBufferFile;
    private File           tempDir;
    private int            width, height;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private byte[]         frameBytes;
    private OutputStream   doomStdin;
    private RandomAccessFile rafFramebuffer; // kept open between ticks — no open/close overhead

    // Doom key codes (doom-generic protocol)
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

    public boolean init(String wadPath, int width, int height) {
        this.width      = width;
        this.height     = height;
        this.frameBytes = new byte[width * height * 4]; // RGBA

        try {
            tempDir         = Files.createTempDirectory("doommod").toFile();
            frameBufferFile = new File(tempDir, "framebuffer.raw");

            // Extract bundled doom binary
            File doomExe = extractDoomExecutable(tempDir);
            if (doomExe == null) {
                System.err.println("[DoomMod] No doom executable available for this platform.");
                return false;
            }

            // Check WAD
            File wad = new File(wadPath);
            if (!wad.exists()) {
                System.err.println("[DoomMod] WAD file not found: " + wadPath);
                return false;
            }

            // Launch doom-generic-minecraft
            List<String> cmd = new ArrayList<>();
            cmd.add(doomExe.getAbsolutePath());
            cmd.add("-iwad");   cmd.add(wad.getAbsolutePath());
            cmd.add("-framebuffer"); cmd.add(frameBufferFile.getAbsolutePath());
            cmd.add("-width");  cmd.add(String.valueOf(width));
            cmd.add("-height"); cmd.add(String.valueOf(height));
            cmd.add("-nomusic");
            cmd.add("-nosound");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tempDir);
            pb.redirectErrorStream(true);
            doomProcess = pb.start();
            doomStdin   = doomProcess.getOutputStream();
            running.set(true);

            // Drain stdout/stderr so doom doesn't block
            Thread drain = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(doomProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        System.out.println("[Doom] " + line);
                } catch (IOException ignored) {}
            });
            drain.setDaemon(true);
            drain.start();

            // Wait for framebuffer file to appear (up to 5 s)
            long deadline = System.currentTimeMillis() + 5000;
            while (!frameBufferFile.exists() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                if (!doomProcess.isAlive()) {
                    System.err.println("[DoomMod] Doom process died before writing framebuffer.");
                    return false;
                }
            }

            if (!frameBufferFile.exists()) {
                System.err.println("[DoomMod] Framebuffer never appeared — doom may have crashed.");
                return false;
            }

            // Open framebuffer RAF once, keep open for all ticks
            rafFramebuffer = new RandomAccessFile(frameBufferFile, "r");
            return true;

        } catch (Exception e) {
            System.err.println("[DoomMod] Failed to start doom: " + e.getMessage());
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
