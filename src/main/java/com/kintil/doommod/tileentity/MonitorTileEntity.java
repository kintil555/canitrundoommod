package com.kintil.doommod.tileentity;

import com.kintil.doommod.util.DoomEngine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public class MonitorTileEntity extends TileEntity implements ITickable {

    private boolean powered = false;
    private EnumFacing activeFace = EnumFacing.NORTH;
    private boolean playerLocked = false;
    private String lockedPlayerName = "";

    // Doom state
    private boolean doomLoaded = false;
    private String doomWadPath = "";

    // Client-side doom engine reference
    private DoomEngine doomEngine = null;

    // Screen pixel buffer - 320x200 doom native resolution
    public static final int DOOM_WIDTH = 320;
    public static final int DOOM_HEIGHT = 200;
    private int[] pixelBuffer = new int[DOOM_WIDTH * DOOM_HEIGHT];

    public void togglePower(EnumFacing face) {
        powered = !powered;
        if (powered) {
            activeFace = face;
        } else {
            stopDoom();
        }
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    public void togglePlayerLock(EntityPlayer player) {
        if (!powered) return;
        if (playerLocked && lockedPlayerName.equals(player.getName())) {
            playerLocked = false;
            lockedPlayerName = "";
        } else {
            playerLocked = true;
            lockedPlayerName = player.getName();
        }
        markDirty();
    }

    public boolean isPowered() { return powered; }
    public boolean isDoomLoaded() { return doomLoaded; }
    public boolean isPlayerLocked() { return playerLocked; }
    public String getLockedPlayerName() { return lockedPlayerName; }
    public EnumFacing getActiveFace() { return activeFace; }
    public int[] getPixelBuffer() { return pixelBuffer; }
    public String getDoomWadPath() { return doomWadPath; }

    public void loadDoom(String wadPath) {
        this.doomWadPath = wadPath;
        if (doomEngine == null) {
            doomEngine = new DoomEngine();
        }
        doomLoaded = doomEngine.init(wadPath, DOOM_WIDTH, DOOM_HEIGHT);
        markDirty();
    }

    public void stopDoom() {
        if (doomEngine != null) {
            doomEngine.destroy();
            doomEngine = null;
        }
        doomLoaded = false;
    }

    public void sendKeyEvent(int keyCode, boolean pressed) {
        if (doomEngine != null && doomLoaded) {
            doomEngine.sendKeyEvent(keyCode, pressed);
        }
    }

    @Override
    public void update() {
        if (world != null && world.isRemote && powered && doomLoaded && doomEngine != null) {
            doomEngine.tick(pixelBuffer);
            // force re-render
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setBoolean("powered", powered);
        compound.setBoolean("doomLoaded", doomLoaded);
        compound.setString("doomWadPath", doomWadPath);
        compound.setBoolean("playerLocked", playerLocked);
        compound.setString("lockedPlayerName", lockedPlayerName);
        compound.setInteger("activeFace", activeFace.getIndex());
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        powered = compound.getBoolean("powered");
        doomLoaded = compound.getBoolean("doomLoaded");
        doomWadPath = compound.getString("doomWadPath");
        playerLocked = compound.getBoolean("playerLocked");
        lockedPlayerName = compound.getString("lockedPlayerName");
        int faceIdx = compound.getInteger("activeFace");
        activeFace = EnumFacing.getFront(faceIdx);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
