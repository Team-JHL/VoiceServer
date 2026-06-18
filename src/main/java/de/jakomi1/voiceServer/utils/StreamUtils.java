package de.jakomi1.voiceServer.utils;

import de.jakomi1.voiceServer.VoiceServer;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamUtils {

    private static final int FRAME_SIZE = 960;
    private static final int MAX_BUFFERED_SAMPLES = 48_000 * 3; // ca. 3 Sekunden Puffer

    private static final Map<String, StreamSession> STREAMS = new ConcurrentHashMap<>();

    public static void startStream(Player from, Player to, CommandSender requester) {
        if (from == null || to == null) return;

        if (from.getUniqueId().equals(to.getUniqueId())) {
            MessageUtils.sendError(requester, "Quelle und Ziel dürfen nicht identisch sein.");
            return;
        }

        String key = sessionKey(from.getName(), to.getName());

        if (STREAMS.containsKey(key)) {
            MessageUtils.sendError(requester, "Stream läuft bereits: " + key);
            return;
        }

        var connection = VoiceServer.serverApi.getConnectionOf(to.getUniqueId());
        if (connection == null) {
            MessageUtils.sendError(requester, to.getName() + " ist nicht im Voicechat verbunden.");
            return;
        }

        EntityAudioChannel channel = VoiceServer.serverApi.createEntityAudioChannel(
                UUID.randomUUID(),
                connection.getPlayer()
        );

        if (channel == null) {
            MessageUtils.sendError(requester, "Audio-Channel konnte nicht erstellt werden.");
            return;
        }

        StreamSession session = new StreamSession(from.getUniqueId(), to.getUniqueId(), key, requester);

        AudioPlayer player = VoiceServer.serverApi.createAudioPlayer(
                channel,
                VoiceServer.serverApi.createEncoder(),
                session::nextFrame
        );

        session.player = player;
        STREAMS.put(key, session);

        player.startPlaying();

        MessageUtils.sendSuccess(requester,
                "Live-Stream gestartet: " + from.getName() + " -> " + to.getName());
    }

    public static void stopStream(String rawKey) {
        if (rawKey == null) return;

        String key = normalizeKey(rawKey);
        StreamSession session = STREAMS.remove(key);

        if (session == null) return;

        try {
            session.stop();
        } catch (Exception ignored) {
        }

        if (session.requester != null) {
            Player from = org.bukkit.Bukkit.getPlayer(session.fromUuid);
            Player to = org.bukkit.Bukkit.getPlayer(session.toUuid);

            String fromName = from != null ? from.getName() : session.fromUuid.toString();
            String toName = to != null ? to.getName() : session.toUuid.toString();

            MessageUtils.sendSuccess(session.requester,
                    "Live-Stream beendet: " + fromName + " -> " + toName);
        }
    }

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (event == null || event.getSenderConnection() == null || event.getPacket() == null) return;

        UUID senderUuid = event.getSenderConnection().getPlayer().getUuid();
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) return;

        for (StreamSession session : STREAMS.values()) {
            if (!session.fromUuid.equals(senderUuid)) continue;

            try {
                short[] pcm = session.decoder.decode(opus);
                if (pcm != null && pcm.length > 0) {
                    session.push(pcm);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static List<String> getActiveKeys() {
        return new ArrayList<>(STREAMS.keySet());
    }

    private static String sessionKey(String from, String to) {
        return normalizeKey(from + "-" + to);
    }

    private static String normalizeKey(String input) {
        return input.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }

    private static class StreamSession {
        private final UUID fromUuid;
        private final UUID toUuid;
        private final String key;
        private final CommandSender requester;
        private final OpusDecoder decoder;
        private final ConcurrentLinkedQueue<Short> buffer = new ConcurrentLinkedQueue<>();
        private final AtomicInteger bufferedSamples = new AtomicInteger(0);

        private volatile AudioPlayer player;

        private StreamSession(UUID fromUuid, UUID toUuid, String key, CommandSender requester) {
            this.fromUuid = fromUuid;
            this.toUuid = toUuid;
            this.key = key;
            this.requester = requester;
            this.decoder = VoiceServer.serverApi.createDecoder();
        }

        private void push(short[] pcm) {
            for (short sample : pcm) {
                buffer.offer(sample);

                int now = bufferedSamples.incrementAndGet();
                if (now > MAX_BUFFERED_SAMPLES) {
                    Short dropped = buffer.poll();
                    if (dropped != null) {
                        bufferedSamples.decrementAndGet();
                    }
                }
            }
        }

        private short[] nextFrame() {
            short[] frame = new short[FRAME_SIZE];

            for (int i = 0; i < FRAME_SIZE; i++) {
                Short s = buffer.poll();
                if (s == null) {
                    break;
                }

                frame[i] = s;
                bufferedSamples.decrementAndGet();
            }

            return frame;
        }

        private void stop() {
            try {
                if (player != null) {
                    player.stopPlaying();
                }
            } catch (Exception ignored) {
            }

            try {
                decoder.close();
            } catch (Exception ignored) {
            }
        }
    }
}