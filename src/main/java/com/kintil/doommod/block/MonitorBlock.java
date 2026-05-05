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
        setLightLevel(0f);
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
        if (worldIn.isRemote) {
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof MonitorTileEntity) {
                MonitorTileEntity monitor = (MonitorTileEntity) te;
                boolean isSneaking = playerIn.isSneaking();
                // Shift + right click = lock/unlock player to monitor
                if (isSneaking) {
                    monitor.togglePlayerLock(playerIn);
                } else {
                    // Right click = toggle monitor on/face
                    monitor.togglePower(facing);
                    // Open GUI if powered
                    if (monitor.isPowered()) {
                        com.kintil.doommod.client.gui.GuiMonitor.open(monitor, pos);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public int getLightValue(IBlockState state) {
        // We can't access world here directly in the override, return max when block exists
        // Actual light is handled by the TileEntity powered state via markDirty
        return 0;
    }
}
