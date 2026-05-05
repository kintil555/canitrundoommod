package com.kintil.doommod;

import com.kintil.doommod.block.ModBlocks;
import com.kintil.doommod.item.MonitorItem;
import com.kintil.doommod.network.PacketHandler;
import com.kintil.doommod.proxy.CommonProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = DoomMod.MODID, name = DoomMod.NAME, version = DoomMod.VERSION)
public class DoomMod {
    public static final String MODID = "doommod";
    public static final String NAME = "Can It Run Doom?";
    public static final String VERSION = "1.0.0";

    @Mod.Instance
    public static DoomMod instance;

    @SidedProxy(
        clientSide = "com.kintil.doommod.proxy.ClientProxy",
        serverSide = "com.kintil.doommod.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static CreativeTabs creativeTab = new CreativeTabs("doommod") {
        @SideOnly(Side.CLIENT)
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ModBlocks.MONITOR_BLOCK);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModBlocks.register();
        PacketHandler.registerMessages();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
