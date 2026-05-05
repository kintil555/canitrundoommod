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
     * Called server-side when player right-clicks.
     * Requires EXACTLY GRID_W x GRID_H solid rectangle of monitor blocks.
     * Clicked block can be any block in the cluster — we auto-find the origin.
     */
    public void activate(EnumFacing face) {
        if (world == null) return;

        EnumFacing right = face.rotateY();
        EnumFacing up    = EnumFacing.UP;

        // Try every possible offset: assume clicked block is at grid position (ox, oy)
        BlockPos[][] winningPositions = null;
        BlockPos     winningOrigin    = null;

        outer:
        for (int ox = 0; ox < GRID_W; ox++) {
            for (int oy = 0; oy < GRID_H; oy++) {
                BlockPos origin = pos
                        .offset(right.getOpposite(), ox)
                        .offset(up.getOpposite(),    oy);
                BlockPos[][] positions = new BlockPos[GRID_W][GRID_H];
                boolean valid = true;
                for (int gx = 0; gx < GRID_W && valid; gx++) {
                    for (int gy = 0; gy < GRID_H && valid; gy++) {
                        BlockPos candidate = origin.offset(right, gx).offset(up, gy);
                        if (isMonitorAt(candidate)) {
                            positions[gx][gy] = candidate;
                        } else {
                            valid = false;
                        }
                    }
                }
                if (valid) {
                    winningPositions = positions;
                    winningOrigin    = origin;
                    break outer;
                }
            }
        }

        if (winningPositions == null) {
            // Cluster incomplete — tell nearby players
            String msg = "\u00a7cNeed a solid " + GRID_W + "\u00d7" + GRID_H
                    + " rectangle of Monitor blocks (" + (GRID_W * GRID_H) + " total)!";
            net.minecraft.util.text.TextComponentString txt =
                    new net.minecraft.util.text.TextComponentString(msg);
            for (net.minecraft.entity.player.EntityPlayer p : world.playerEntities) {
                if (p.getDistanceSq(pos) < 64) p.sendMessage(txt);
            }
            return;
        }

        // Setup master (origin block)
        TileEntity originTE = world.getTileEntity(winningOrigin);
        if (!(originTE instanceof MonitorTileEntity)) return;
        MonitorTileEntity masterTE = (MonitorTileEntity) originTE;
        masterTE.powered    = true;
        masterTE.isMaster   = true;
        masterTE.masterPos  = null;
        masterTE.activeFace = face;
        masterTE.gridX      = 0;
        masterTE.gridY      = 0;
        masterTE.syncToClients();

        // Setup all other blocks as slaves
        for (int gx = 0; gx < GRID_W; gx++) {
            for (int gy = 0; gy < GRID_H; gy++) {
                if (winningPositions[gx][gy].equals(winningOrigin)) continue;
                TileEntity te = world.getTileEntity(winningPositions[gx][gy]);
                if (te instanceof MonitorTileEntity)
                    ((MonitorTileEntity) te).setupAsSlave(winningOrigin, face, gx, gy);
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

    /**
     * Server-side only: persist the WAD path so it syncs to clients via NBT.
     * Clients will start DoomEngine themselves in onDataPacket().
     * Never call loadDoom() on the server — DoomEngine needs a display context.
     */
    public void saveWadPath(String wadPath) {
        this.doomWadPath = wadPath;
        this.doomLoaded  = true; // optimistic: client will confirm on init
        syncToClients();
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

        // If doomLoaded was restored from NBT but engine never started (e.g. world reload),
        // kick off initialisation now.
        if (doomLoaded && doomEngine == null && !doomWadPath.isEmpty()) {
            loadDoom(doomWadPath);
            return;
        }

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
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        String prevWadPath = this.doomWadPath;
        boolean prevLoaded = this.doomLoaded;
        readFromNBT(pkt.getNbtCompound());

        // Client-side: if server synced a wadPath and we haven't started DoomEngine yet,
        // start it now. This is the ONLY place DoomEngine is initialised — never on the server.
        if (world != null && world.isRemote && isMaster) {
            boolean pathChanged = !doomWadPath.isEmpty() && !doomWadPath.equals(prevWadPath);
            boolean notRunning  = doomEngine == null || !prevLoaded;
            if (doomLoaded && (pathChanged || notRunning)) {
                loadDoom(doomWadPath); // real init on client
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isMonitorAt(BlockPos p) {
        if (world == null) return false;
        TileEntity te = world.getTileEntity(p);
        return te instanceof MonitorTileEntity;
    }
}
