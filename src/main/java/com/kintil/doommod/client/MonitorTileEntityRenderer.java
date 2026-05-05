package com.kintil.doommod.client;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MonitorTileEntityRenderer extends TileEntitySpecialRenderer<MonitorTileEntity> {

    // One GL texture per master TileEntity (keyed by master BlockPos long)
    private final Map<Long, Integer> masterTextures = new HashMap<>();

    @Override
    public void render(MonitorTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {

        if (!te.isPowered()) return;

        // ── Resolve master ────────────────────────────────────────────────────
        MonitorTileEntity master;
        int gx, gy;

        if (te.isMaster()) {
            master = te;
            gx = 0; gy = 0;
        } else {
            BlockPos mp = te.getMasterPos();
            if (mp == null) return;
            TileEntity masterTe = getWorld().getTileEntity(mp);
            if (!(masterTe instanceof MonitorTileEntity)) return;
            master = (MonitorTileEntity) masterTe;
            if (!master.isPowered()) return;
            gx = te.getGridX();
            gy = te.getGridY();
        }

        // ── Ensure GL texture exists for this master ──────────────────────────
        long key = master.getPos().toLong();
        int tex;
        if (!masterTextures.containsKey(key)) {
            tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            ByteBuffer empty = ByteBuffer.allocateDirect(
                    MonitorTileEntity.DOOM_WIDTH * MonitorTileEntity.DOOM_HEIGHT * 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, empty);
            masterTextures.put(key, tex);
        } else {
            tex = masterTextures.get(key);
        }

        // ── Upload pixel buffer when this IS the master block ─────────────────
        if (te.isMaster() && master.isDoomLoaded()) {
            uploadFrame(master, tex);
        }

        // ── Calculate UV slice for this block in the grid ─────────────────────
        // Grid: GRID_W columns, GRID_H rows.  gx=0 is left, gy=0 is BOTTOM (Minecraft Y).
        // UV: u increases left→right, v increases top→bottom.
        // So gy=0 (bottom block) maps to v near 1.0, gy=GRID_H-1 (top) maps to v near 0.0.

        int GW = MonitorTileEntity.GRID_W;
        int GH = MonitorTileEntity.GRID_H;

        float uMin = (float) gx       / GW;
        float uMax = (float)(gx + 1)  / GW;
        float vMin = (float)(GH - 1 - gy) / GH; // flip Y
        float vMax = (float)(GH     - gy) / GH;

        // ── GL render ─────────────────────────────────────────────────────────
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        applyFaceTransform(te.getActiveFace());

        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Quad sits exactly on the front face of the block
        float h = 0.5005f; // just in front of block surface

        if (master.isDoomLoaded()) {
            // Render the Doom framebuffer slice
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(uMin, vMin); GL11.glVertex3f(-0.5f,  0.5f, h);
            GL11.glTexCoord2f(uMax, vMin); GL11.glVertex3f( 0.5f,  0.5f, h);
            GL11.glTexCoord2f(uMax, vMax); GL11.glVertex3f( 0.5f, -0.5f, h);
            GL11.glTexCoord2f(uMin, vMax); GL11.glVertex3f(-0.5f, -0.5f, h);
            GL11.glEnd();
        } else {
            // No game loaded: bright white standby screen (like a CRT powering on)
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();

            // Bright white background
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor3f(0.95f, 0.95f, 0.95f);
            GL11.glVertex3f(-0.5f,  0.5f, h);
            GL11.glVertex3f( 0.5f,  0.5f, h);
            GL11.glVertex3f( 0.5f, -0.5f, h);
            GL11.glVertex3f(-0.5f, -0.5f, h);
            GL11.glEnd();

            // Scanline overlay — thin dark horizontal stripes like a real CRT
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 0; i <= 20; i++) {
                float yPos = -0.5f + (float) i / 20;
                GL11.glColor4f(0f, 0f, 0f, 0.10f);
                GL11.glVertex3f(-0.5f, yPos, h + 0.0001f);
                GL11.glVertex3f( 0.5f, yPos, h + 0.0001f);
            }
            GL11.glEnd();

            // Blue tint center strip — "no signal" look
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(0.4f, 0.6f, 1.0f, 0.18f);
            GL11.glVertex3f(-0.5f,  0.08f, h + 0.0002f);
            GL11.glVertex3f( 0.5f,  0.08f, h + 0.0002f);
            GL11.glVertex3f( 0.5f, -0.08f, h + 0.0002f);
            GL11.glVertex3f(-0.5f, -0.08f, h + 0.0002f);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_BLEND);

            GL11.glColor3f(1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Upload the full DOOM pixel buffer to the master's GL texture. */
    private void uploadFrame(MonitorTileEntity master, int tex) {
        int[] pixels = master.getPixelBuffer();
        byte[] rgba  = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            rgba[i*4]     = (byte)((argb >> 16) & 0xFF); // R
            rgba[i*4 + 1] = (byte)((argb >>  8) & 0xFF); // G
            rgba[i*4 + 2] = (byte)( argb        & 0xFF); // B
            rgba[i*4 + 3] = (byte)0xFF;                   // A
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
    }

    /**
     * Rotate so that the +Z axis of our local space points OUT of the block face,
     * and the quad is drawn on that face.
     */
    private void applyFaceTransform(EnumFacing face) {
        switch (face) {
            case NORTH: GlStateManager.rotate(180,  0, 1, 0); break; // -Z face, flip 180
            case SOUTH:                                         break; // +Z face, no rotation
            case EAST:  GlStateManager.rotate( 90,  0, 1, 0); break;
            case WEST:  GlStateManager.rotate(-90,  0, 1, 0); break;
            case UP:    GlStateManager.rotate(-90,  1, 0, 0); break;
            case DOWN:  GlStateManager.rotate( 90,  1, 0, 0); break;
        }
    }
}
