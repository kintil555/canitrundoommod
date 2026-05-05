package com.kintil.doommod.tileentity;

import com.kintil.doommod.util.DoomEngine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

public class MonitorTileEntity extends TileEntity implements ITickable {

    // ── Doom screen dimensions ────────────────────────────────────────────────
    public static final int DOOM_WIDTH  = 320;
    public static final int DOOM_HEIGHT = 200;

    // ── Grid dimensions (blocks) ──────────────────────────────────────────────
    // The monitor cluster is GRID_W blocks wide × GRID_H blocks tall.
    // These match a 4:5 aspect ratio (width:height) laid on the wall.
    public static final int GRID_W = 5; // horizontal blocks
    public static final int GRID_H = 4; // vertical blocks

    // ── Persistent state ──────────────────────────────────────────────────────
    private boolean   powered       = false;
    private EnumFacing activeFace   = EnumFacing.NORTH;
    private boolean   playerLocked  = false;
    private String    lockedPlayerName = "";
    private boolean   doomLoaded    = false;
    private String    doomWadPath   = "";

    // ── Master / slave cluster state ──────────────────────────────────────────
    // isMaster == true  → this block owns the DoomEngine and pixel buffer
    // isMaster == false → gridX/gridY tell where in the master's screen this block is
    private boolean  isMaster = false;
    private BlockPos masterPos = null; // null when this IS the master
    private int      gridX = 0;       // 0‥GRID_W-1, column in the screen
    private int      gridY = 0;       // 0‥GRID_H-1, row    in the screen (0 = top)

    // ── Pixel buffer (master only) ────────────────────────────────────────────
    private int[] pixelBuffer = new int[DOOM_WIDTH * DOOM_HEIGHT];

    // ── Doom engine (master, client-side only) ────────────────────────────────
    private DoomEngine doomEngine = null;

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    public boolean isPowered()         { return powered; }
    public boolean isDoomLoaded()      { return doomLoaded; }
    public boolean isPlayerLocked()    { return playerLocked; }
    public String  getLockedPlayerName(){ return lockedPlayerName; }
    public EnumFacing getActiveFace()  { return activeFace; }
    public int[]   getPixelBuffer()    { return pixelBuffer; }
    public String  getDoomWadPath()    { return doomWadPath; }
    public boolean isMaster()          { return isMaster; }
    public BlockPos getMasterPos()     { return masterPos; }
    public int     getGridX()          { return gridX; }
    public int     getGridY()          { return gridY; }

    /**
     * Called when the player right-clicks this block.
     * Scans the neighbourhood for a 4×5 (or smaller) cluster of monitor blocks,
     * marks this block as master, marks every neighbour as slave, and powers on.
     */
    public void activate(EnumFacing face) {
        if (world == null) return;

        // Build cluster starting from THIS block as the origin.
        // We scan along the two axes perpendicular to `face`.
        // right-axis = face.rotateY()  (horizontal)
        // up-axis    = UP              (always vertical)
        EnumFacing right = face.rotateY();
        EnumFacing up    = EnumFacing.UP;

        // Find the bottom-left corner of the cluster (max extend left & down)
        // We try to place this block so the cluster is centred on it, then clamp.
        // Simpler: scan up to GRID_W right and GRID_H up from current pos.

        // First collect all monitor blocks in a bounding box
        // around this block that share the same facing axis.
        boolean[][] found = new boolean[GRID_W][GRID_H];
        BlockPos[][] positions = new BlockPos[GRID_W][GRID_H];

        // Anchor: this block = (0,0). Find how far left/down we can go.
        // For simplicity we place origin at this block and build rightward/upward.
        // Players are expected to click the bottom-left block of a pre-placed array.
        // Actually: scan a GRID_W × GRID_H window with this block at bottom-left corner.

        BlockPos origin = pos;

        int clusterW = 0, clusterH = 0;
        for (int gx = 0; gx < GRID_W; gx++) {
            for (int gy = 0; gy < GRID_H; gy++) {
                BlockPos candidate = origin
                    .offset(right, gx)
                    .offset(up,    gy);
                if (isMonitorAt(candidate)) {
                    found[gx][gy] = true;
                    positions[gx][gy] = candidate;
                    if (gx + 1 > clusterW) clusterW = gx + 1;
                    if (gy + 1 > clusterH) clusterH = gy + 1;
                }
            }
        }

        if (clusterW == 0 || clusterH == 0) return;

        // Mark this block as master
        powered     = true;
        isMaster    = true;
        masterPos   = null;
        activeFace  = face;
        gridX = 0;
        gridY = 0;
        syncToClients();

        // Mark each other block as slave
        for (int gx = 0; gx < clusterW; gx++) {
            for (int gy = 0; gy < clusterH; gy++) {
                if (gx == 0 && gy == 0) continue; // that's us
                if (!found[gx][gy]) continue;
                TileEntity te = world.getTileEntity(positions[gx][gy]);
                if (te instanceof MonitorTileEntity) {
                    MonitorTileEntity slave = (MonitorTileEntity) te;
                    slave.setupAsSlave(pos, face, gx, gy);
                }
            }
        }
    }

    /** Called by master to set up a neighbouring block as a slave. */
    public void setupAsSlave(BlockPos master, EnumFacing face, int gx, int gy) {
        powered    = true;
        isMaster   = false;
        masterPos  = master;
        activeFace = face;
        gridX = gx;
        gridY = gy;
        syncToClients();
    }

    public void deactivate() {
        powered = false;
        stopDoom();
        markDirty();
        if (world != null)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
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

    public void loadDoom(String wadPath) {
        this.doomWadPath = wadPath;
        if (doomEngine == null) doomEngine = new DoomEngine();
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
        if (doomEngine != null && doomLoaded)
            doomEngine.sendKeyEvent(keyCode, pressed);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ITickable
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void update() {
        if (world == null || !world.isRemote) return;
        if (!powered || !isMaster) return;
        if (!doomLoaded || doomEngine == null) return;

        doomEngine.tick(pixelBuffer);
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NBT
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        c.setBoolean("powered",       powered);
        c.setBoolean("doomLoaded",    doomLoaded);
        c.setString ("doomWadPath",   doomWadPath);
        c.setBoolean("playerLocked",  playerLocked);
        c.setString ("lockedPlayer",  lockedPlayerName);
        c.setInteger("activeFace",    activeFace.getIndex());
        c.setBoolean("isMaster",      isMaster);
        c.setInteger("gridX",         gridX);
        c.setInteger("gridY",         gridY);
        if (masterPos != null) {
            c.setLong("masterPos", masterPos.toLong());
        }
        return super.writeToNBT(c);
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        powered           = c.getBoolean("powered");
        doomLoaded        = c.getBoolean("doomLoaded");
        doomWadPath       = c.getString ("doomWadPath");
        playerLocked      = c.getBoolean("playerLocked");
        lockedPlayerName  = c.getString ("lockedPlayer");
        activeFace        = EnumFacing.values()[c.getInteger("activeFace")];
        isMaster          = c.getBoolean("isMaster");
        gridX             = c.getInteger("gridX");
        gridY             = c.getInteger("gridY");
        masterPos         = c.hasKey("masterPos") ? BlockPos.fromLong(c.getLong("masterPos")) : null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Network sync
    // ═════════════════════════════════════════════════════════════════════════

    private void syncToClients() {
        markDirty();
        if (world != null)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 1, getUpdateTag()); }
    @Override public NBTTagCompound getUpdateTag()             { return writeToNBT(new NBTTagCompound()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isMonitorAt(BlockPos p) {
        if (world == null) return false;
        TileEntity te = world.getTileEntity(p);
        return te instanceof MonitorTileEntity;
    }
}
