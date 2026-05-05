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
 * Overlay GUI for the monitor cluster.
 *
 * States:
 *  1. No WAD found        → shows instructions.
 *  2. Loading (async)     → animated progress bar + live log line.
 *  3. Error               → red message.
 *  4. Doom running        → transparent, forwards keys; ESC closes.
 */
@SideOnly(Side.CLIENT)
public class GuiMonitor extends GuiScreen {

    private final MonitorTileEntity master;
    private final BlockPos          masterPos;

    // Status when no WAD found (before any load attempt)
    private String noWadMsg = "";

    // Dot-animation counter for the progress bar
    private int animTick = 0;

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

        // Only trigger WAD search if nothing is happening yet
        if (!master.isDoomLoaded() && master.loadProgress == 0) {
            autoLoadWad();
        }
    }

    // ── Auto WAD detection ────────────────────────────────────────────────────

    private void autoLoadWad() {
        File mcDir   = Minecraft.getMinecraft().gameDir;
        File doomDir = new File(mcDir, "doom");

        if (!doomDir.exists()) {
            doomDir.mkdirs();
            noWadMsg = "\u00a7ePut your DOOM.WAD in:\n" + doomDir.getAbsolutePath();
            return;
        }

        File[] wads = doomDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wad"));

        if (wads == null || wads.length == 0) {
            noWadMsg = "\u00a7ePut your DOOM.WAD in:\n" + doomDir.getAbsolutePath();
            return;
        }

        // Prefer DOOM1.WAD, DOOM.WAD, then DOOM2.WAD, else first found
        File chosen = wads[0];
        for (File w : wads) {
            String n = w.getName().toLowerCase();
            if (n.equals("doom1.wad") || n.equals("doom.wad")) { chosen = w; break; }
            if (n.equals("doom2.wad")) chosen = w;
        }

        String path = chosen.getAbsolutePath();

        // Load on client (DoomEngine); persist path on server via packet
        master.loadDoom(path);
        PacketHandler.INSTANCE.sendToServer(new PacketLoadWad(masterPos, path));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public void updateScreen() {
        animTick++;
    }

    // ── Draw ─────────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int cx = width / 2, cy = height / 2;

        if (master.isDoomLoaded()) {
            // ── Running — transparent overlay ──────────────────────────────────
            drawString(fontRenderer,
                    "\u00a7fESC \u00a77to close",
                    4, 4, 0x88FFFFFF);
            return;
        }

        int pct = master.loadProgress;

        if (pct == 0 && !noWadMsg.isEmpty()) {
            // ── No WAD found ───────────────────────────────────────────────────
            drawDark(cx, cy, 220, 50);
            drawCenteredString(fontRenderer, "\u00a7eCAN IT RUN DOOM?", cx, cy - 22, 0xFFFFFF);
            for (String line : noWadMsg.split("\n")) {
                drawCenteredString(fontRenderer, line, cx, cy - 8, 0xFFFFFF);
                cy += 12;
            }
            return;
        }

        if (pct < 0) {
            // ── Error ──────────────────────────────────────────────────────────
            drawDark(cx, cy, 220, 50);
            drawCenteredString(fontRenderer, "\u00a7cDOOM FAILED TO START", cx, cy - 22, 0xFFFFFF);
            String err = master.loadStatus;
            if (err.length() > 48) err = err.substring(0, 45) + "...";
            drawCenteredString(fontRenderer, "\u00a7c" + err, cx, cy - 8, 0xFFFFFF);
            return;
        }

        // ── Loading progress bar ───────────────────────────────────────────────
        int panelW = 240, panelH = 70;
        int px = cx - panelW / 2, py = cy - panelH / 2;

        // Panel background
        drawRect(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xFF111111);
        drawRect(px,     py,     px + panelW,     py + panelH,     0xFF1A1A2E);

        // Title
        drawCenteredString(fontRenderer, "\u00a7e\u00a7lCAN IT RUN DOOM?", cx, py + 6, 0xFFFFFF);

        // Progress bar track
        int barX = px + 8, barY = py + 22, barW = panelW - 16, barH = 10;
        drawRect(barX - 1,      barY - 1, barX + barW + 1, barY + barH + 1, 0xFF444444);
        drawRect(barX,          barY,     barX + barW,     barY + barH,     0xFF222222);

        // Filled portion — gradient green→yellow→red based on pct
        int filled = (int) (barW * pct / 100.0f);
        if (filled > 0) {
            int col = pct < 50 ? 0xFF22BB55
                    : pct < 90 ? 0xFFDDAA00
                    :            0xFF44DD44;
            drawRect(barX, barY, barX + filled, barY + barH, col);
        }

        // Animated shimmer block on the leading edge
        if (pct < 100 && filled > 0) {
            int shimX = barX + filled - 4;
            int shimAlpha = (int) (128 + 127 * Math.sin(animTick * 0.3));
            drawRect(shimX, barY, shimX + 4, barY + barH,
                    (shimAlpha << 24) | 0xFFFFFF);
        }

        // Percentage text
        drawCenteredString(fontRenderer, pct + "%", cx, barY + barH + 3, 0xFFCCCCCC);

        // Status log line — strip Minecraft colour codes for length check
        String status = master.loadStatus;
        if (status == null) status = "";
        // Trim to fit the panel
        while (fontRenderer.getStringWidth(status) > panelW - 12 && status.length() > 4)
            status = status.substring(0, status.length() - 4) + "...";

        // Animate dots when not done
        String dots = "";
        if (pct > 0 && pct < 100) {
            int d = (animTick / 8) % 4;
            dots = ".".repeat(d);
        }
        drawCenteredString(fontRenderer, "\u00a77" + status + dots, cx, py + panelH - 12, 0xFFFFFF);
    }

    /** Draw a dark centred panel. */
    private void drawDark(int cx, int cy, int halfW, int halfH) {
        drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, 0xCC000000);
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

