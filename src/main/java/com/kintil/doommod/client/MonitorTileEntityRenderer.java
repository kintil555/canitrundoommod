package com.kintil.doommod.client;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
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

import static org.lwjgl.opengl.GL11.*;

@SideOnly(Side.CLIENT)
public class MonitorTileEntityRenderer extends TileEntitySpecialRenderer<MonitorTileEntity> {

    private final Map<Long, Integer> masterTextures = new HashMap<>();
    private final Map<Long, Long>    turnOnStart    = new HashMap<>();
    private static final long TURN_ON_MS = 150L;

    @Override
    public void render(MonitorTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {

        if (!te.isPowered()) return;

        // Resolve master
        MonitorTileEntity master;
        int gx, gy;
        if (te.isMaster()) {
            master = te; gx = 0; gy = 0;
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

        // Turn-on animation
        long masterKey = master.getPos().toLong();
        if (!turnOnStart.containsKey(masterKey))
            turnOnStart.put(masterKey, System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - turnOnStart.get(masterKey);
        float animT = Math.min(1.0f, (float) elapsed / TURN_ON_MS);

        // GL texture for this master
        int tex;
        if (!masterTextures.containsKey(masterKey)) {
            tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            ByteBuffer empty = ByteBuffer.allocateDirect(
                    MonitorTileEntity.DOOM_WIDTH * MonitorTileEntity.DOOM_HEIGHT * 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, empty);
            masterTextures.put(masterKey, tex);
        } else {
            tex = masterTextures.get(masterKey);
        }

        if (te.isMaster() && master.isDoomLoaded())
            uploadFrame(master, tex);

        // UV slice
        int GW = MonitorTileEntity.GRID_W;
        int GH = MonitorTileEntity.GRID_H;
        float uMin = (float)  gx      / GW;
        float uMax = (float)(gx + 1)  / GW;
        float vMin = (float)(GH - 1 - gy) / GH;
        float vMax = (float)(GH     - gy) / GH;

        // ── WebDisplays-style: fullbright, no lighting, no lightmap ──────────
        RenderHelper.disableStandardItemLighting();
        setLightmapDisabled(true);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        applyFaceTransform(te.getActiveFace());

        // Turn-on animation: scale X/Y (WebDisplays does same thing)
        if (animT < 1.0f)
            GlStateManager.scale(animT, animT, 1.0f);

        float h = 0.5005f;

        if (master.isDoomLoaded()) {
            glBindTexture(GL_TEXTURE_2D, tex);
            glBegin(GL_QUADS);
            glColor4f(1f, 1f, 1f, 1f);
            glTexCoord2f(uMin, vMin); glVertex3f(-0.5f,  0.5f, h);
            glTexCoord2f(uMax, vMin); glVertex3f( 0.5f,  0.5f, h);
            glTexCoord2f(uMax, vMax); glVertex3f( 0.5f, -0.5f, h);
            glTexCoord2f(uMin, vMax); glVertex3f(-0.5f, -0.5f, h);
            glEnd();
            GlStateManager.bindTexture(0);
        } else {
            // Standby: bright white "no signal" screen
            glDisable(GL_TEXTURE_2D);
            glBegin(GL_QUADS);
            glColor3f(0.95f, 0.95f, 0.95f);
            glVertex3f(-0.5f,  0.5f, h);
            glVertex3f( 0.5f,  0.5f, h);
            glVertex3f( 0.5f, -0.5f, h);
            glVertex3f(-0.5f, -0.5f, h);
            glEnd();

            // Scanlines
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_LINES);
            for (int i = 0; i <= 20; i++) {
                float yPos = -0.5f + (float) i / 20f;
                glColor4f(0f, 0f, 0f, 0.10f);
                glVertex3f(-0.5f, yPos, h + 0.0001f);
                glVertex3f( 0.5f, yPos, h + 0.0001f);
            }
            glEnd();
            // Blue center strip
            glBegin(GL_QUADS);
            glColor4f(0.4f, 0.6f, 1.0f, 0.18f);
            glVertex3f(-0.5f,  0.08f, h + 0.0002f);
            glVertex3f( 0.5f,  0.08f, h + 0.0002f);
            glVertex3f( 0.5f, -0.08f, h + 0.0002f);
            glVertex3f(-0.5f, -0.08f, h + 0.0002f);
            glEnd();
            glDisable(GL_BLEND);
            glColor3f(1f, 1f, 1f);
            glEnable(GL_TEXTURE_2D);
        }

        GlStateManager.popMatrix();

        // Restore MC state — exactly like WebDisplays
        RenderHelper.enableStandardItemLighting();
        setLightmapDisabled(false);
        glEnable(GL_CULL_FACE);
    }

    private void uploadFrame(MonitorTileEntity master, int tex) {
        int[] pixels = master.getPixelBuffer();
        byte[] rgba  = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            rgba[i*4]     = (byte)((argb >> 16) & 0xFF);
            rgba[i*4 + 1] = (byte)((argb >>  8) & 0xFF);
            rgba[i*4 + 2] = (byte)( argb        & 0xFF);
            rgba[i*4 + 3] = (byte) 0xFF;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
    }

    private void applyFaceTransform(EnumFacing face) {
        switch (face) {
            case NORTH: GlStateManager.rotate(180,  0, 1, 0); break;
            case SOUTH:                                         break;
            case EAST:  GlStateManager.rotate( 90,  0, 1, 0); break;
            case WEST:  GlStateManager.rotate(-90,  0, 1, 0); break;
            case UP:    GlStateManager.rotate(-90,  1, 0, 0); break;
            case DOWN:  GlStateManager.rotate( 90,  1, 0, 0); break;
        }
    }
}
