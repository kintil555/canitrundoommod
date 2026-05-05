package com.kintil.doommod.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

public class PacketHandler {
    public static SimpleNetworkWrapper INSTANCE;

    public static void registerMessages() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("doommod");
        INSTANCE.registerMessage(PacketLoadWad.Handler.class, PacketLoadWad.class, 0,
                net.minecraftforge.fml.relauncher.Side.SERVER);
    }
}
