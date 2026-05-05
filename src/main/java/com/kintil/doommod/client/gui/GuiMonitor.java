package com.kintil.doommod.client.gui;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import com.kintil.doommod.util.DoomEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@SideOnly(Side.CLIENT)
public class GuiMonitor extends GuiScreen {

    private MonitorTileEntity monitor;
    private BlockPos pos;
    private GuiTextField wadPathField;
    private GuiButton loadButton;
    private GuiButton browseButton;
    private int doomTexture = -1;
    private boolean lockedToMonitor = false;

    // Doom screen dimensions on GUI (scaled from 320x200)
    private static final int DOOM_RENDER_W = 640;
    private static final int DOOM_RENDER_H = 400;

    public static void open(MonitorTileEntity te, BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMonitor(te, pos));
    }

    public GuiMonitor(MonitorTileEntity te, BlockPos pos) {
        this.monitor = te;
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int centerX = width / 2;
        int bottomY = height / 2 + DOOM_RENDER_H / 2 + 10;

        wadPathField = new GuiTextField(0, fontRenderer, centerX - 180, bottomY, 300, 20);
        wadPathField.setMaxStringLength(512);
        wadPathField.setText(monitor.getDoomWadPath());
        wadPathField.setFocused(true);

        loadButton = new GuiButton(1, centerX + 130, bottomY, 70, 20, "Load");
        browseButton = new GuiButton(2, centerX - 220, bottomY, 30, 20, "...");

        buttonList.add(loadButton);
        buttonList.add(browseButton);

        // Allocate OpenGL texture for doom screen
        if (doomTexture == -1) {
            doomTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, doomTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            // Initialize with black
            int[] black = new int[MonitorTileEntity.DOOM_WIDTH * MonitorTileEntity.DOOM_HEIGHT];
            IntBuffer buf = ByteBuffer.allocateDirect(black.length * 4).asIntBuffer();
            buf.put(black).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, MonitorTileEntity.DOOM_WIDTH,
                    MonitorTileEntity.DOOM_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Dark overlay background
        drawDefaultBackground();

        int centerX = width / 2;
        int centerY = height / 2;

        int screenX = centerX - DOOM_RENDER_W / 2;
        int screenY = centerY - DOOM_RENDER_H / 2 - 10;

        // Draw monitor bezel
        drawRect(screenX - 8, screenY - 8, screenX + DOOM_RENDER_W + 8, screenY + DOOM_RENDER_H + 8, 0xFF222222);
        drawRect(screenX - 4, screenY - 4, screenX + DOOM_RENDER_W + 4, screenY + DOOM_RENDER_H + 4, 0xFF444444);

        if (!monitor.isDoomLoaded()) {
            // Show "INSERT WAD FILE" screen
            drawRect(screenX, screenY, screenX + DOOM_RENDER_W, screenY + DOOM_RENDER_H, 0xFF000000);
            drawCenteredString(fontRenderer, "\u00a7aCAN IT RUN DOOM?", centerX, screenY + DOOM_RENDER_H / 2 - 30, 0xFFFFFF);
            drawCenteredString(fontRenderer, "\u00a7eNo game loaded.", centerX, screenY + DOOM_RENDER_H / 2 - 10, 0xFFFFFF);
            drawCenteredString(fontRenderer, "\u00a77Enter path to DOOM.WAD below and click Load", centerX, screenY + DOOM_RENDER_H / 2 + 10, 0xFFFFFF);
            drawCenteredString(fontRenderer, "\u00a77or click \u00a7f[...]\u00a77 to browse for WAD file", centerX, screenY + DOOM_RENDER_H / 2 + 25, 0xFFFFFF);
        } else {
            // Render doom framebuffer
            uploadDoomFrame();
            GlStateManager.enableTexture2D();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, doomTexture);
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawTexturedRect(screenX, screenY, DOOM_RENDER_W, DOOM_RENDER_H);

            if (lockedToMonitor) {
                drawString(fontRenderer, "\u00a7cLOCKED - Shift+RClick to unlock", screenX + 5, screenY + 5, 0xFFFFFF);
            } else {
                drawString(fontRenderer, "\u00a7eSHIFT+RClick to lock input", screenX + 5, screenY + 5, 0xFFFFFF);
            }
        }

        // WAD path input
        int bottomY = screenY + DOOM_RENDER_H + 10;
        drawString(fontRenderer, "WAD Path:", centerX - 220, bottomY + 5, 0xAAAAAA);
        wadPathField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTexturedRect(int x, int y, int w, int h) {
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void uploadDoomFrame() {
        int[] pixels = monitor.getPixelBuffer();
        // Convert ARGB to RGBA for OpenGL
        byte[] rgba = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            rgba[i * 4]     = (byte)((argb >> 16) & 0xFF); // R
            rgba[i * 4 + 1] = (byte)((argb >> 8) & 0xFF);  // G
            rgba[i * 4 + 2] = (byte)(argb & 0xFF);           // B
            rgba[i * 4 + 3] = (byte)0xFF;                    // A
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, doomTexture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            // Load button
            String path = wadPathField.getText().trim();
            if (!path.isEmpty()) {
                monitor.loadDoom(path);
            }
        } else if (button.id == 2) {
            // Browse button - open AWT file chooser (runs on render thread, fine for GUI)
            openFileBrowser();
        }
    }

    private void openFileBrowser() {
        // Run file chooser in separate thread to avoid blocking render
        Thread t = new Thread(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select DOOM WAD file");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "DOOM WAD Files (*.wad)", "wad", "WAD"));
            // Try to start at .minecraft folder
            File mcDir = Minecraft.getMinecraft().gameDir;
            File doomDir = new File(mcDir, "doom");
            if (doomDir.exists()) chooser.setCurrentDirectory(doomDir);
            else chooser.setCurrentDirectory(mcDir);

            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                // Update on main thread
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    wadPathField.setText(path);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (monitor.isDoomLoaded() && lockedToMonitor) {
            // Forward key to doom
            int doomKey = lwjglKeyToDoom(keyCode);
            if (doomKey != -1) {
                monitor.sendKeyEvent(doomKey, true);
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        wadPathField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_RETURN) {
            String path = wadPathField.getText().trim();
            if (!path.isEmpty()) {
                monitor.loadDoom(path);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (doomTexture != -1) {
            GL11.glDeleteTextures(doomTexture);
            doomTexture = -1;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Maps LWJGL key codes to DOOM key codes.
     */
    private int lwjglKeyToDoom(int lwjgl) {
        switch (lwjgl) {
            case Keyboard.KEY_UP:      return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_DOWN:    return DoomEngine.DOOM_KEY_DOWN;
            case Keyboard.KEY_LEFT:    return DoomEngine.DOOM_KEY_LEFT;
            case Keyboard.KEY_RIGHT:   return DoomEngine.DOOM_KEY_RIGHT;
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_RCONTROL: return DoomEngine.DOOM_KEY_FIRE;
            case Keyboard.KEY_SPACE:   return DoomEngine.DOOM_KEY_USE;
            case Keyboard.KEY_RETURN:  return DoomEngine.DOOM_KEY_ENTER;
            case Keyboard.KEY_ESCAPE:  return DoomEngine.DOOM_KEY_ESCAPE;
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_RSHIFT:  return DoomEngine.DOOM_KEY_STRAFE_L;
            case Keyboard.KEY_A:       return DoomEngine.DOOM_KEY_STRAFE_L;
            case Keyboard.KEY_D:       return DoomEngine.DOOM_KEY_STRAFE_R;
            case Keyboard.KEY_W:       return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_S:       return DoomEngine.DOOM_KEY_DOWN;
            default: return -1;
        }
    }
}
