package de.jakomi1.voiceServer.listener;

import de.jakomi1.voiceServer.Scheduler;
import de.jakomi1.voiceServer.utils.DataUtils;
import de.jakomi1.voiceServer.utils.GroupUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static de.jakomi1.voiceServer.VoiceServer.plugin;

public class JoinListener implements Listener {

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        DataUtils.updatePermissions(event.getPlayer());

        Scheduler.runLater(() -> {
            if (event.getPlayer().isOnline()) {
                GroupUtils.joinDefaultGroup(event.getPlayer());
            }
        }, 40L);
    }
}