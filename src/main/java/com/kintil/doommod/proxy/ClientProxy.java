package com.kintil.doommod.proxy;

import com.kintil.doommod.block.ModBlocks;
import com.kintil.doommod.client.MonitorTileEntityRenderer;
import com.kintil.doommod.tileentity.MonitorTileEntity;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // Register item model for monitor block
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(ModBlocks.MONITOR_BLOCK), 0,
            new ModelResourceLocation("doommod:monitor", "inventory")
        );
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientRegistry.bindTileEntitySpecialRenderer(MonitorTileEntity.class, new MonitorTileEntityRenderer());
    }
}
