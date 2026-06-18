package de.jakomi1.voiceServer.listener;

import de.jakomi1.voiceServer.Scheduler;
import de.jakomi1.voiceServer.utils.DataUtils;
import de.jakomi1.voiceServer.utils.GroupUtils;
import de.jakomi1.voiceServer.utils.RecorderUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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