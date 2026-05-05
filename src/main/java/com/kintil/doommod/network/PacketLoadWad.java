package com.kintil.doommod.network;

import com.kintil.doommod.tileentity.MonitorTileEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.nio.charset.StandardCharsets;

public class PacketLoadWad implements IMessage {
    private BlockPos pos;
    private String wadPath;

    public PacketLoadWad() {}

    public PacketLoadWad(BlockPos pos, String wadPath) {
        this.pos = pos;
        this.wadPath = wadPath;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        wadPath = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        byte[] bytes = wadPath.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<PacketLoadWad, IMessage> {
        @Override
        public IMessage onMessage(PacketLoadWad message, MessageContext ctx) {
            // Runs on SERVER — only save wadPath to NBT so it persists and syncs to clients.
            // DoomEngine must NEVER be started on the server (no display/rendering context).
            ctx.getServerHandler().player.getServer().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof MonitorTileEntity) {
                    ((MonitorTileEntity) te).saveWadPath(message.wadPath);
                }
            });
            return null;
        }
    }
}
