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

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new MonitorTileEntity();
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state,
                                    EntityPlayer playerIn, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {

        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof MonitorTileEntity)) return true;
        MonitorTileEntity monitor = (MonitorTileEntity) te;

        if (playerIn.isSneaking()) {
            // Shift+click: toggle power off for whole cluster
            if (!worldIn.isRemote) {
                turnOffCluster(worldIn, monitor);
            }
            return true;
        }

        // Normal click: power on cluster (server side sets state, client opens GUI)
        if (!worldIn.isRemote) {
            monitor.activate(facing);
        } else {
            // Client side: if already powered and is/has master, open GUI
            MonitorTileEntity master = getMaster(worldIn, monitor);
            if (master != null && master.isPowered()) {
                com.kintil.doommod.client.gui.GuiMonitor.open(master, master.getPos());
            }
        }
        return true;
    }

    /** Walk the cluster (by master reference) and deactivate all blocks. */
    private void turnOffCluster(World world, MonitorTileEntity clicked) {
        MonitorTileEntity master = getMaster(world, clicked);
        if (master == null) return;
        // Deactivate master (stops doom engine)
        master.deactivate();
        // Scan nearby for slaves that point to this master
        BlockPos mp = master.getPos();
        for (int dx = -MonitorTileEntity.GRID_W; dx <= MonitorTileEntity.GRID_W; dx++) {
            for (int dy = -MonitorTileEntity.GRID_H; dy <= MonitorTileEntity.GRID_H; dy++) {
                for (int dz = -MonitorTileEntity.GRID_W; dz <= MonitorTileEntity.GRID_W; dz++) {
                    TileEntity te = world.getTileEntity(mp.add(dx, dy, dz));
                    if (te instanceof MonitorTileEntity) {
                        MonitorTileEntity m = (MonitorTileEntity) te;
                        if (!m.isMaster() && mp.equals(m.getMasterPos())) {
                            m.deactivate();
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

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
}
