package org.zeroBzeroT.agm;

import io.netty.channel.*;
import net.minecraft.server.v1_12_R1.PacketPlayInFlying;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

/*
    Created by John
    7/1/2019
    this is the first thing i've done with packets in a plugin so pls don't make fun of me
 */
public class Main extends JavaPlugin implements Listener {
    public final HashMap<UUID, Integer> movedInsideVehicle = new HashMap<>();
    public final HashMap<UUID, Integer> violationCounter = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        injectPlayer(player);

        movedInsideVehicle.put(uuid, 0);
        violationCounter.put(uuid, 0);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        removePlayer(player);

        movedInsideVehicle.remove(uuid);
        violationCounter.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getPlayer().getVehicle() != null) {
            UUID uuid = player.getUniqueId();

            movedInsideVehicle.put(uuid, 1);

            if (!violationCounter.containsKey(uuid)) {
                violationCounter.put(uuid, 0);
            } else if (violationCounter.get(uuid) > 3) {
                Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.RED + player.getName() + " tried getting into god mode");
                player.leaveVehicle();
                event.getPlayer().kickPlayer("Invalid data.");

                removePlayer(player);

                movedInsideVehicle.remove(uuid);
                violationCounter.remove(uuid);
            }
        }
    }

    private void removePlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

        channel.eventLoop().submit(() -> {
            channel.pipeline().remove(player.getName());
            return null;
        });
    }

    private void injectPlayer(Player player) {
        ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {

            @Override
            public void channelRead(ChannelHandlerContext channelHandlerContext, Object packet) throws Exception {
                UUID uuid = player.getUniqueId();

                if (packet instanceof PacketPlayInFlying.PacketPlayInPosition && movedInsideVehicle.get(uuid) > 0) {
                    int count = violationCounter.getOrDefault(uuid, 0);
                    violationCounter.put(uuid, count + 1);
                    movedInsideVehicle.put(uuid, 0);
                }

                super.channelRead(channelHandlerContext, packet);
            }

            @Override
            public void write(ChannelHandlerContext channelHandlerContext, Object packet, ChannelPromise channelPromise) throws Exception {
                super.write(channelHandlerContext, packet, channelPromise);
            }
        };

        ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();
        pipeline.addBefore("packet_handler", player.getName(), channelDuplexHandler);
    }
}