package de.jakomi1.voiceServer.listener;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;

import java.util.UUID;

import static de.jakomi1.voiceServer.util.GroupUtils.groupPasswords;

public class CreateGroupListener {
    public static void onGroupCreatedEvent(CreateGroupEvent event) {
        Group group = event.getGroup();
        assert group != null;
        UUID id = group.getId();
        boolean hasPw = group.hasPassword();
        if (hasPw) {
            groupPasswords.put(id, null);
        }
    }
}
