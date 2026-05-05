package com.kintil.doommod.client.gui;

import com.kintil.doommod.network.PacketHandler;
import com.kintil.doommod.network.PacketLoadWad;
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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Overlay GUI shown when the player right-clicks a powered monitor cluster.
 *
 * If no WAD is loaded:  shows a "insert WAD" prompt drawn as an on-screen overlay.
 * If WAD is loaded:     transparent – just forwards keyboard input to Doom.
 */
@SideOnly(Side.CLIENT)
public class GuiMonitor extends GuiScreen {

    private final MonitorTileEntity master;
    private final BlockPos          masterPos;
    private GuiTextField            wadPathField;
    private GuiButton               loadButton;
    private GuiButton               browseButton;

    public static void open(MonitorTileEntity master, BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMonitor(master, pos));
    }

    public GuiMonitor(MonitorTileEntity master, BlockPos pos) {
        this.master    = master;
        this.masterPos = pos;
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int cx = width / 2;
        int cy = height / 2;

        wadPathField = new GuiTextField(0, fontRenderer,
                cx - 150, cy + 60, 260, 20);
        wadPathField.setMaxStringLength(512);
        wadPathField.setText(master.getDoomWadPath());
        wadPathField.setFocused(true);

        loadButton   = new GuiButton(1, cx + 120, cy + 60, 60,  20, "Load");
        browseButton = new GuiButton(2, cx - 190, cy + 60, 30,  20, "...");

        buttonList.add(loadButton);
        buttonList.add(browseButton);
    }

    // ── Draw ─────────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int cx = width / 2;
        int cy = height / 2;

        if (!master.isDoomLoaded()) {
            // Semi-transparent dark panel in centre of screen
            drawRect(cx - 200, cy - 80, cx + 200, cy + 90, 0xCC000000);

            drawCenteredString(fontRenderer, "\u00a7aCAN IT RUN DOOM?",          cx, cy - 65, 0xFFFFFF);
            drawCenteredString(fontRenderer, "\u00a7eNo game loaded.",            cx, cy - 45, 0xFFFFFF);
            drawCenteredString(fontRenderer,
                    "\u00a77Enter path to DOOM.WAD below and click Load",         cx, cy - 25, 0xFFFFFF);
            drawCenteredString(fontRenderer,
                    "\u00a77or click \u00a7f[...]\u00a77 to browse for WAD file", cx, cy -  5, 0xFFFFFF);

            drawString(fontRenderer, "WAD Path:", cx - 190, cy + 65, 0xAAAAAA);
            wadPathField.drawTextBox();
            loadButton.drawButton(mc, mouseX, mouseY, partialTicks);
            browseButton.drawButton(mc, mouseX, mouseY, partialTicks);
        } else {
            // Doom is running — draw minimal HUD hint
            int hint = 0x88FFFFFF;
            drawString(fontRenderer,
                    master.isPlayerLocked()
                            ? "\u00a7cLOCKED \u00a77— Shift+RClick to unlock"
                            : "\u00a7eShift+RClick \u00a77to lock input | \u00a7fESC\u00a77 to close",
                    4, 4, hint);
        }

        // Don't call super — we don't want the vanilla background dimming the world
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) { // Load
            String path = wadPathField.getText().trim();
            if (!path.isEmpty()) {
                master.loadDoom(path);
                // Also send to server so state is saved
                PacketHandler.INSTANCE.sendToServer(new PacketLoadWad(masterPos, path));
            }
        } else if (button.id == 2) { // Browse
            openFileBrowser();
        }
    }

    private void openFileBrowser() {
        Thread t = new Thread(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select DOOM WAD file");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "DOOM WAD Files (*.wad)", "wad", "WAD"));
            File mcDir  = Minecraft.getMinecraft().gameDir;
            File doomDir = new File(mcDir, "doom");
            chooser.setCurrentDirectory(doomDir.exists() ? doomDir : mcDir);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                Minecraft.getMinecraft().addScheduledTask(() -> wadPathField.setText(path));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (master.isDoomLoaded()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(null);
                return;
            }
            // Forward keys to Doom
            int dk = lwjglToDoom(keyCode);
            if (dk != -1) master.sendKeyEvent(dk, true);
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
                master.loadDoom(path);
                PacketHandler.INSTANCE.sendToServer(new PacketLoadWad(masterPos, path));
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    // ── Key mapping ───────────────────────────────────────────────────────────

    private int lwjglToDoom(int k) {
        switch (k) {
            case Keyboard.KEY_UP:       return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_DOWN:     return DoomEngine.DOOM_KEY_DOWN;
            case Keyboard.KEY_LEFT:     return DoomEngine.DOOM_KEY_LEFT;
            case Keyboard.KEY_RIGHT:    return DoomEngine.DOOM_KEY_RIGHT;
            case Keyboard.KEY_W:        return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_S:        return DoomEngine.DOOM_KEY_DOWN;
            case Keyboard.KEY_A:        return DoomEngine.DOOM_KEY_STRAFE_L;
            case Keyboard.KEY_D:        return DoomEngine.DOOM_KEY_STRAFE_R;
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_RCONTROL: return DoomEngine.DOOM_KEY_FIRE;
            case Keyboard.KEY_SPACE:    return DoomEngine.DOOM_KEY_USE;
            case Keyboard.KEY_RETURN:   return DoomEngine.DOOM_KEY_ENTER;
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_RSHIFT:   return DoomEngine.DOOM_KEY_STRAFE_L;
            default:                    return -1;
        }
    }
}
