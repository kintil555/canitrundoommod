package com.kintil.doommod.client;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MonitorTileEntityRenderer extends TileEntitySpecialRenderer<MonitorTileEntity> {

    // One texture per tile entity (by pos hashcode)
    private final Map<Long, Integer> textures = new HashMap<>();

    @Override
    public void render(MonitorTileEntity te, double x, double y, double z,
                        float partialTicks, int destroyStage, float alpha) {
        if (!te.isPowered() || !te.isDoomLoaded()) return;

        long key = te.getPos().toLong();
        int tex;
        if (!textures.containsKey(key)) {
            tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            // Allocate
            ByteBuffer buf = ByteBuffer.allocateDirect(MonitorTileEntity.DOOM_WIDTH * MonitorTileEntity.DOOM_HEIGHT * 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            textures.put(key, tex);
        } else {
            tex = textures.get(key);
        }

        // Upload pixel buffer
        int[] pixels = te.getPixelBuffer();
        byte[] rgba = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            rgba[i * 4]     = (byte)((argb >> 16) & 0xFF);
            rgba[i * 4 + 1] = (byte)((argb >> 8) & 0xFF);
            rgba[i * 4 + 2] = (byte)(argb & 0xFF);
            rgba[i * 4 + 3] = (byte)0xFF;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                MonitorTileEntity.DOOM_WIDTH, MonitorTileEntity.DOOM_HEIGHT,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        // Render quad on the active face
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        EnumFacing face = te.getActiveFace();
        setupFaceTransform(face);

        GlStateManager.enableTexture2D();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableLighting();

        float half = 0.499f;
        float offset = 0.501f; // Slightly in front of block face

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex3f(-half, half, offset);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex3f(half, half, offset);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex3f(half, -half, offset);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex3f(-half, -half, offset);
        GL11.glEnd();

        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void setupFaceTransform(EnumFacing face) {
        switch (face) {
            case NORTH: GlStateManager.rotate(0, 0, 1, 0); break;
            case SOUTH: GlStateManager.rotate(180, 0, 1, 0); break;
            case EAST:  GlStateManager.rotate(-90, 0, 1, 0); break;
            case WEST:  GlStateManager.rotate(90, 0, 1, 0); break;
            case UP:    GlStateManager.rotate(-90, 1, 0, 0); break;
            case DOWN:  GlStateManager.rotate(90, 1, 0, 0); break;
        }
    }
}
