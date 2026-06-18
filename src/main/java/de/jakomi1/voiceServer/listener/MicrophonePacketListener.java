package de.jakomi1.voiceServer.listener;

import de.jakomi1.voiceServer.utils.RecorderUtils;
import de.jakomi1.voiceServer.utils.StreamUtils;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

public class MicrophonePacketListener {

    public static void onPacketEvent(MicrophonePacketEvent event) {
        if (event == null || event.getSenderConnection() == null || event.getPacket() == null) {
            return;
        }

        try {
            RecorderUtils.onMicrophonePacket(event);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            StreamUtils.onMicrophonePacket(event);
        } catch (Exception e) {
            e.printStackTrace();
        }



    }
}