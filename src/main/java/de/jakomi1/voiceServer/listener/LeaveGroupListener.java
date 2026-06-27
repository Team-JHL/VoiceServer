package de.jakomi1.voiceServer.listener;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;

import java.util.UUID;

import static de.jakomi1.voiceServer.VoiceServer.serverApi;
import static de.jakomi1.voiceServer.util.GroupUtils.groupPasswords;
import static de.jakomi1.voiceServer.util.GroupUtils.playerGroupMap;

public class LeaveGroupListener {
    public static void onGroupLeaveEvent(LeaveGroupEvent event) {
        VoicechatConnection conn = event.getConnection();
        Group group = event.getGroup();
        if (conn == null || group == null) return;

        UUID uuid = conn.getPlayer().getUuid();
        playerGroupMap.remove(uuid);

        long remaining = playerGroupMap.values().stream()
                .filter(g -> g.getId().equals(group.getId()))
                .count();

        if (remaining == 0 && !group.isPersistent()) {
            groupPasswords.remove(group.getId());
            serverApi.removeGroup(group.getId());
        }
    }
}
