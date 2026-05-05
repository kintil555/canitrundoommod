package com.kintil.doommod.util;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DoomEngine wraps a headless Chocolate-Doom process (or doom-generic),
 * reads the framebuffer via a shared memory file, and forwards key events.
 *
 * How it works:
 *  1. We extract a bundled doom-generic executable from mod jar resources.
 *  2. We launch it with the provided WAD file.
 *  3. Every tick we read the framebuffer (320x200 RGBA raw file) it writes.
 *  4. Key events are written to a named pipe / stdin.
 *
 * For platforms where no executable is available, we fall back to a pure-Java
 * limited DOOM renderer (not implemented here - shows "UNSUPPORTED PLATFORM").
 */
@SideOnly(Side.CLIENT)
public class DoomEngine {

    private Process doomProcess;
    private File frameBufferFile;
    private File keyInputFile;
    private File tempDir;
    private int width, height;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private byte[] frameBytes;
    private OutputStream doomStdin;

    // Key mapping from Minecraft LWJGL keys to DOOM key codes
    // Based on doom-generic key definitions
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
        this.width = width;
        this.height = height;
        this.frameBytes = new byte[width * height * 4]; // RGBA

        try {
            tempDir = Files.createTempDirectory("doommod").toFile();
            frameBufferFile = new File(tempDir, "framebuffer.raw");
            keyInputFile = new File(tempDir, "keyinput.fifo");

            // Extract doom executable for current OS
            File doomExe = extractDoomExecutable(tempDir);
            if (doomExe == null) {
                System.err.println("[DoomMod] No doom executable available for this platform.");
                return false;
            }

            // Verify WAD file exists
            File wad = new File(wadPath);
            if (!wad.exists()) {
                System.err.println("[DoomMod] WAD file not found: " + wadPath);
                return false;
            }

            // Build command
            List<String> cmd = new ArrayList<>();
            cmd.add(doomExe.getAbsolutePath());
            cmd.add("-iwad");
            cmd.add(wad.getAbsolutePath());
            cmd.add("-framebuffer");
            cmd.add(frameBufferFile.getAbsolutePath());
            cmd.add("-width");
            cmd.add(String.valueOf(width));
            cmd.add("-height");
            cmd.add(String.valueOf(height));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tempDir);
            pb.redirectErrorStream(true);
            doomProcess = pb.start();
            doomStdin = doomProcess.getOutputStream();
            running.set(true);

            // Start output drain thread
            Thread drainThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(doomProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Doom] " + line);
                    }
                } catch (IOException ignored) {}
            });
            drainThread.setDaemon(true);
            drainThread.start();

            return true;
        } catch (Exception e) {
            System.err.println("[DoomMod] Failed to start doom: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Called every game tick to update the pixel buffer from the framebuffer file.
     */
    public void tick(int[] pixelBuffer) {
        if (!running.get() || frameBufferFile == null || !frameBufferFile.exists()) return;
        if (doomProcess != null && !doomProcess.isAlive()) {
            running.set(false);
            return;
        }

        try {
            // Read RGBA bytes from shared framebuffer file
            RandomAccessFile raf = new RandomAccessFile(frameBufferFile, "r");
            if (raf.length() >= frameBytes.length) {
                raf.seek(0);
                raf.readFully(frameBytes);
                // Convert RGBA bytes to packed ARGB int array
                for (int i = 0; i < width * height; i++) {
                    int r = frameBytes[i * 4] & 0xFF;
                    int g = frameBytes[i * 4 + 1] & 0xFF;
                    int b = frameBytes[i * 4 + 2] & 0xFF;
                    pixelBuffer[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
            }
            raf.close();
        } catch (Exception ignored) {}
    }

    /**
     * Send key event to doom process via stdin protocol.
     * Protocol: single byte 'K' + key code byte + 1 (pressed) / 0 (released)
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
        if (doomProcess != null && doomProcess.isAlive()) {
            doomProcess.destroy();
        }
        // Cleanup temp dir
        if (tempDir != null && tempDir.exists()) {
            deleteDir(tempDir);
        }
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) deleteDir(f);
        }
        dir.delete();
    }

    /**
     * Extracts the appropriate doom executable from the mod's resources.
     * Executables should be bundled at:
     *   /assets/doommod/bin/doom-linux-x64
     *   /assets/doommod/bin/doom-windows-x64.exe
     *   /assets/doommod/bin/doom-macos-x64
     */
    private File extractDoomExecutable(File targetDir) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String resourceName;
        String outName;

        if (os.contains("win")) {
            resourceName = "/assets/doommod/bin/doom-windows-x64.exe";
            outName = "doom.exe";
        } else if (os.contains("mac")) {
            resourceName = "/assets/doommod/bin/doom-macos-x64";
            outName = "doom";
        } else {
            // Linux
            resourceName = "/assets/doommod/bin/doom-linux-x64";
            outName = "doom";
        }

        InputStream in = DoomEngine.class.getResourceAsStream(resourceName);
        if (in == null) {
            System.err.println("[DoomMod] Doom binary not found in jar: " + resourceName);
            System.err.println("[DoomMod] Please place a doom-generic binary in src/main/resources" + resourceName);
            return null;
        }

        File out = new File(targetDir, outName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
        } catch (IOException e) {
            System.err.println("[DoomMod] Failed to extract doom binary: " + e.getMessage());
            return null;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }

        if (!os.contains("win")) {
            out.setExecutable(true);
        }
        return out;
    }
}
