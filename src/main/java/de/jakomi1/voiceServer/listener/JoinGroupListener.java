package de.jakomi1.voiceServer.listener;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;

import java.util.UUID;

import static de.jakomi1.voiceServer.utils.GroupUtils.playerGroupMap;

public class JoinGroupListener {
    public static void onGroupJoinEvent(JoinGroupEvent event) {
        VoicechatConnection conn = event.getConnection();
        Group group = event.getGroup();
        if (conn == null || group == null) return;
        UUID uuid = conn.getPlayer().getUuid();
        playerGroupMap.put(uuid, group);
    }
}
