package com.kintil.doommod.block;

import com.kintil.doommod.DoomMod;
import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MonitorBlock extends Block implements ITileEntityProvider {

    public MonitorBlock() {
        super(Material.IRON);
        setRegistryName("doommod:monitor");
        setTranslationKey("monitor");
        setHardness(2.0f);
        setSoundType(SoundType.METAL);
        setCreativeTab(DoomMod.creativeTab);
    }

    @Override public TileEntity createNewTileEntity(World w, int meta) { return new MonitorTileEntity(); }
    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public boolean isOpaqueCube(IBlockState s)  { return false; }

    // ── Right-click ───────────────────────────────────────────────────────────

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hx, float hy, float hz) {

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof MonitorTileEntity)) return true;
        MonitorTileEntity monitor = (MonitorTileEntity) te;

        if (player.isSneaking()) {
            // Shift+click → power off entire cluster
            if (!world.isRemote) turnOffCluster(world, monitor);
            return true;
        }

        if (!world.isRemote) {
            // Server: try to activate — will validate cluster size internally
            monitor.activate(facing);
        } else {
            // Client: open GUI only if cluster is already powered
            MonitorTileEntity master = getMaster(world, monitor);
            if (master == null) master = monitor;
            if (master.isPowered()) {
                com.kintil.doommod.client.gui.GuiMonitor.open(master, master.getPos());
            } else {
                // Not yet powered — server will validate; give feedback if cluster is wrong size
                // (message handled server-side via chat)
            }
        }
        return true;
    }

    // ── Block broken → shut down cluster ─────────────────────────────────────

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof MonitorTileEntity) {
                turnOffCluster(world, (MonitorTileEntity) te);
            }
        }
        super.breakBlock(world, pos, state);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void turnOffCluster(World world, MonitorTileEntity clicked) {
        MonitorTileEntity master = getMaster(world, clicked);
        // If clicked block IS master or we found master, shut it and all slaves down
        MonitorTileEntity m = (master != null) ? master : clicked;
        BlockPos mp = m.getPos();

        m.deactivate();

        for (int dx = -MonitorTileEntity.GRID_W; dx <= MonitorTileEntity.GRID_W; dx++) {
            for (int dy = -MonitorTileEntity.GRID_H; dy <= MonitorTileEntity.GRID_H; dy++) {
                for (int dz = -MonitorTileEntity.GRID_W; dz <= MonitorTileEntity.GRID_W; dz++) {
                    TileEntity neighbor = world.getTileEntity(mp.add(dx, dy, dz));
                    if (neighbor instanceof MonitorTileEntity) {
                        MonitorTileEntity mn = (MonitorTileEntity) neighbor;
                        if (!mn.isMaster() && mp.equals(mn.getMasterPos())) {
                            mn.deactivate();
                        }
                    }
                }
            }
        }
    }

    public static MonitorTileEntity getMaster(World world, MonitorTileEntity te) {
        if (te.isMaster()) return te;
        if (te.getMasterPos() == null) return null;
        TileEntity masterTe = world.getTileEntity(te.getMasterPos());
        return masterTe instanceof MonitorTileEntity ? (MonitorTileEntity) masterTe : null;
    }
}
