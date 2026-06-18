package de.jakomi1.voiceServer.utils;

import de.jakomi1.voiceServer.Scheduler;
import de.jakomi1.voiceServer.VoiceServer;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecorderUtils {

    private static final Map<UUID, RecordingSession> RECORDINGS = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy-HH-mm-ss")
                    .withZone(ZoneId.systemDefault());

    private static class RecordingSession {
        final UUID targetUuid;
        final String targetName;
        final long startTime;
        final CommandSender requester;
        final List<byte[]> opusPackets = new ArrayList<>();

        RecordingSession(UUID targetUuid, String targetName, long startTime, CommandSender requester) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.startTime = startTime;
            this.requester = requester;
        }
    }

    public static void startRecording(Player target, int seconds) {
        startRecording(target, seconds, null);
    }

    public static void startRecording(Player target, int seconds, CommandSender requester) {
        if (target == null) return;
        if (seconds <= 0) return;

        UUID uuid = target.getUniqueId();

        if (RECORDINGS.containsKey(uuid)) {
            if (requester != null) {
                MessageUtils.sendError(
                        requester,
                        target.getName() + " wird bereits aufgenommen."
                );
            }
            return;
        }

        RecordingSession session = new RecordingSession(
                uuid,
                target.getName(),
                System.currentTimeMillis(),
                requester
        );

        RECORDINGS.put(uuid, session);

        if (requester != null) {
            MessageUtils.sendSuccess(
                    requester,
                    target.getName() + " wird für " + seconds + " Sekunden aufgenommen."
            );
        }

        Scheduler.runLater(() -> stopRecording(uuid), seconds * 20L);
    }

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (event == null
                || event.getSenderConnection() == null
                || event.getPacket() == null) {
            return;
        }

        UUID uuid = event.getSenderConnection().getPlayer().getUuid();
        RecordingSession session = RECORDINGS.get(uuid);

        if (session == null) {
            return;
        }

        byte[] opus = event.getPacket().getOpusEncodedData();

        if (opus == null || opus.length == 0) {
            return;
        }

        synchronized (session.opusPackets) {
            session.opusPackets.add(opus.clone());
        }
    }

    public static void stopRecording(UUID uuid) {
        RecordingSession session = RECORDINGS.remove(uuid);

        if (session == null) {
            return;
        }

        try {
            File folder = getRecordingsFolder();

            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException(
                        "Could not create recordings folder: "
                                + folder.getAbsolutePath()
                );
            }

            String time = FORMATTER.format(
                    Instant.ofEpochMilli(session.startTime)
            );

            String fileName = sanitizeFileName(
                    session.targetName + "-" + time + ".opus"
            );

            File file = new File(folder, fileName);

            List<byte[]> packets;

            synchronized (session.opusPackets) {
                packets = new ArrayList<>(session.opusPackets);
            }

            if (packets.isEmpty()) {
                VoiceServer.plugin.getLogger().info(
                        "No packets captured for: " + session.targetName
                );
                return;
            }

            OpusFileUtils.writeOggOpus(file, packets);

            if (session.requester != null) {
                MessageUtils.sendSuccess(
                        session.requester,
                        "Aufnahme gespeichert: " + file.getName()
                );
            }

            VoiceServer.plugin.getLogger().info(
                    "Saved recording: " + file.getAbsolutePath()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static File getRecordingsFolder() {
        return new File(
                VoiceServer.plugin.getDataFolder(),
                "recordings"
        );
    }

    private static String sanitizeFileName(String input) {
        if (input == null) {
            return "recording.opus";
        }

        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}