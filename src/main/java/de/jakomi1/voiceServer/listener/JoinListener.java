package de.jakomi1.voiceServer.listener;

import de.jakomi1.voiceServer.utils.DataUtils;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jakomi1.voiceServer.VoiceServer.plugin;

public class JoinListener implements Listener {

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        System.out.println(event.getPlayer().getUniqueId());
        DataUtils.updatePermissions(event.getPlayer());
    }
}
