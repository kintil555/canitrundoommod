package com.kintil.doommod.client.gui;

import com.kintil.doommod.network.PacketHandler;
import com.kintil.doommod.network.PacketLoadWad;
import com.kintil.doommod.tileentity.MonitorTileEntity;
import com.kintil.doommod.util.DoomEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;

/**
 * Minimal overlay GUI for the monitor cluster.
 *
 * - If no WAD is loaded yet: auto-searches .minecraft/doom/ for a .wad file,
 *   loads it immediately. Shows a "looking for WAD..." message if none found.
 * - If WAD is loaded: transparent — just forwards keyboard input to Doom.
 *   ESC closes the GUI.
 */
@SideOnly(Side.CLIENT)
public class GuiMonitor extends GuiScreen {

    private final MonitorTileEntity master;
    private final BlockPos          masterPos;
    private String                  statusMsg = "";

    public static void open(MonitorTileEntity master, BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMonitor(master, pos));
    }

    public GuiMonitor(MonitorTileEntity master, BlockPos pos) {
        this.master    = master;
        this.masterPos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        // Auto-load WAD if not already loaded
        if (!master.isDoomLoaded()) {
            autoLoadWad();
        }
    }

    // ── Auto WAD detection ────────────────────────────────────────────────────

    private void autoLoadWad() {
        // Search in .minecraft/doom/ folder for any .wad file
        File mcDir   = Minecraft.getMinecraft().gameDir;
        File doomDir = new File(mcDir, "doom");

        if (!doomDir.exists()) {
            doomDir.mkdirs();
            statusMsg = "\u00a7ePut your DOOM.WAD in: " + doomDir.getAbsolutePath();
            return;
        }

        File[] wads = doomDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".wad"));

        if (wads == null || wads.length == 0) {
            statusMsg = "\u00a7ePut your DOOM.WAD in: " + doomDir.getAbsolutePath();
            return;
        }

        // Prefer DOOM1.WAD, DOOM.WAD, DOOM2.WAD in that order; else just first found
        File chosen = wads[0];
        for (File w : wads) {
            String n = w.getName().toLowerCase();
            if (n.equals("doom1.wad") || n.equals("doom.wad")) { chosen = w; break; }
            if (n.equals("doom2.wad")) chosen = w;
        }

        String path = chosen.getAbsolutePath();
        statusMsg = "\u00a7aLoading: " + chosen.getName() + "...";

        // Load on client (DoomEngine runs here)
        master.loadDoom(path);
        // Persist on server via packet
        PacketHandler.INSTANCE.sendToServer(new PacketLoadWad(masterPos, path));
    }

    // ── Draw ─────────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (master.isDoomLoaded()) {
            // Doom running — just show a tiny hint
            drawString(fontRenderer,
                    "\u00a7fESC\u00a77 to close",
                    4, 4, 0x88FFFFFF);
        } else {
            // Show status / instructions
            int cx = width / 2, cy = height / 2;
            drawRect(cx - 210, cy - 30, cx + 210, cy + 30, 0xCC000000);
            drawCenteredString(fontRenderer, "\u00a7aCAN IT RUN DOOM?", cx, cy - 18, 0xFFFFFF);
            drawCenteredString(fontRenderer, statusMsg.isEmpty()
                    ? "\u00a77Searching for WAD..." : statusMsg, cx, cy, 0xFFFFFF);
        }
        // No super.drawScreen() — don't dim the world
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        if (master.isDoomLoaded()) {
            int dk = lwjglToDoom(keyCode);
            if (dk != -1) master.sendKeyEvent(dk, true);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    // ── Key mapping ───────────────────────────────────────────────────────────

    private int lwjglToDoom(int k) {
        switch (k) {
            case Keyboard.KEY_UP:        return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_DOWN:      return DoomEngine.DOOM_KEY_DOWN;
            case Keyboard.KEY_LEFT:      return DoomEngine.DOOM_KEY_LEFT;
            case Keyboard.KEY_RIGHT:     return DoomEngine.DOOM_KEY_RIGHT;
            case Keyboard.KEY_W:         return DoomEngine.DOOM_KEY_UP;
            case Keyboard.KEY_S:         return DoomEngine.DOOM_KEY_DOWN;
            case Keyboard.KEY_A:         return DoomEngine.DOOM_KEY_STRAFE_L;
            case Keyboard.KEY_D:         return DoomEngine.DOOM_KEY_STRAFE_R;
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_RCONTROL:  return DoomEngine.DOOM_KEY_FIRE;
            case Keyboard.KEY_SPACE:     return DoomEngine.DOOM_KEY_USE;
            case Keyboard.KEY_RETURN:    return DoomEngine.DOOM_KEY_ENTER;
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_RSHIFT:    return DoomEngine.DOOM_KEY_STRAFE_L;
            default:                     return -1;
        }
    }
}
