package com.kintil.doommod.block;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ModBlocks {
    public static MonitorBlock MONITOR_BLOCK;

    public static void register() {
        MONITOR_BLOCK = new MonitorBlock();
        ForgeRegistries.BLOCKS.register(MONITOR_BLOCK);
        ForgeRegistries.ITEMS.register(new ItemBlock(MONITOR_BLOCK)
                .setRegistryName(MONITOR_BLOCK.getRegistryName()));

        GameRegistry.registerTileEntity(MonitorTileEntity.class, "doommod:monitor_te");
    }
}
