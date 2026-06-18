package de.jakomi1.voiceServer.utils;

import de.jakomi1.voiceServer.VoiceServer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoundboardUtils {

    private static final int FRAME_SIZE = 960;

    private static final AudioFormat FORMAT = new AudioFormat(48000F, 16, 1, true, false);

    public static void play(CommandSender sender, String target, String folder, String fileName, int volume) {
        try {
            File file = resolve(new File(VoiceServer.plugin.getDataFolder(), folder), fileName);

            if (file == null) {
                MessageUtils.sendError(sender, "File not found: " + fileName);
                return;
            }

            short[] pcm = load(file);

            if (pcm.length == 0) {
                MessageUtils.sendError(sender, "Audio file is empty or unsupported: " + file.getName());
                return;
            }

            boolean played = false;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!match(p, sender, target)) continue;

                VoicechatConnection con = VoiceServer.serverApi.getConnectionOf(p.getUniqueId());
                if (con == null) continue;

                EntityAudioChannel channel = VoiceServer.serverApi.createEntityAudioChannel(UUID.randomUUID(), con.getPlayer());
                if (channel == null) continue;

                AudioPlayer player = VoiceServer.serverApi.createAudioPlayer(
                        channel,
                        VoiceServer.serverApi.createEncoder(),
                        new PCMFrameSupplier(pcm, volume)
                );

                player.startPlaying();

                SoundSessionManager.add(
                        p.getUniqueId(),
                        new ActiveSound(stripExtension(file.getName()), System.currentTimeMillis(), player, volume)
                );

                played = true;
            }

            if (!played) {
                MessageUtils.sendError(sender, "No valid target found.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            MessageUtils.sendError(sender, "Playback error");
        }
    }

    public static void stop(CommandSender sender, String target, String soundName) {
        boolean stoppedAny = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!match(p, sender, target)) continue;

            if (soundName == null || soundName.isBlank()) {
                SoundSessionManager.stopAll(p.getUniqueId());
                stoppedAny = true;
                continue;
            }

            SoundSessionManager.stop(p.getUniqueId(), soundName);
            stoppedAny = true;
        }

        if (!stoppedAny) {
            MessageUtils.sendError(sender, "No valid target found.");
        }
    }

    public static List<String> getActiveSoundNames(CommandSender sender, String target) {
        Set<String> names = new LinkedHashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!match(p, sender, target)) continue;

            for (ActiveSound s : SoundSessionManager.get(p.getUniqueId())) {
                names.add(s.name());
            }
        }

        return new ArrayList<>(names);
    }

    private static boolean match(Player p, CommandSender sender, String target) {
        if (target == null) return false;

        if (target.equalsIgnoreCase("@a")) return true;

        if (target.equalsIgnoreCase("@s")) {
            return sender instanceof Player sp && sp.getUniqueId().equals(p.getUniqueId());
        }

        if (target.equalsIgnoreCase("@p")) {
            if (!(sender instanceof Player sp)) {
                return !Bukkit.getOnlinePlayers().isEmpty()
                        && Bukkit.getOnlinePlayers().iterator().next().getUniqueId().equals(p.getUniqueId());
            }
            return sp.getUniqueId().equals(p.getUniqueId());
        }

        return p.getName().equalsIgnoreCase(target);
    }

    private static File resolve(File folder, String name) {
        if (!folder.exists()) folder.mkdirs();

        if (name == null || name.isBlank()) return null;

        name = unquote(name);

        File direct = new File(folder, name);
        if (direct.exists() && direct.isFile()) {
            return direct;
        }

        File wav = new File(folder, name + ".wav");
        if (wav.exists() && wav.isFile()) {
            return wav;
        }

        File opus = new File(folder, name + ".opus");
        if (opus.exists() && opus.isFile()) {
            return opus;
        }

        File[] files = folder.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if (n.equalsIgnoreCase(name)
                    || n.equalsIgnoreCase(name + ".wav")
                    || n.equalsIgnoreCase(name + ".opus")) {
                return f;
            }
        }

        return null;
    }

    private static short[] load(File file) throws Exception {
        String lower = file.getName().toLowerCase(Locale.ROOT);

        if (lower.endsWith(".wav") || isWav(file)) {
            return loadWav(file);
        }

        if (lower.endsWith(".opus") || isOgg(file)) {
            short[] opus = loadOggOpus(file);
            if (opus.length > 0) return opus;
        }

        try {
            return loadWav(file);
        } catch (Exception ignored) {
        }

        try {
            return loadOggOpus(file);
        } catch (Exception ignored) {
        }

        return new short[0];
    }

    private static short[] loadWav(File file) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file);
             AudioInputStream pcm = AudioSystem.getAudioInputStream(FORMAT, in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[4096];
            int r;

            while ((r = pcm.read(buf)) != -1) {
                out.write(buf, 0, r);
            }

            return bytesToShorts(out.toByteArray());
        }
    }

    private static short[] loadOggOpus(File file) throws Exception {
        OpusDecoder decoder = VoiceServer.serverApi.createDecoder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            byte[] pageData;
            int[] lacing;

            while (true) {
                OggPage page = readOggPage(in);
                if (page == null) break;

                pageData = page.data;
                lacing = page.segmentLengths;

                int offset = 0;
                for (int segLen : lacing) {
                    if (segLen < 0 || offset + segLen > pageData.length) {
                        throw new IOException("Invalid Ogg page in " + file.getName());
                    }

                    packet.write(pageData, offset, segLen);
                    offset += segLen;

                    if (segLen < 255) {
                        byte[] opusPacket = packet.toByteArray();
                        packet.reset();

                        if (opusPacket.length == 0) {
                            continue;
                        }

                        if (isOpusHeaderPacket(opusPacket)) {
                            continue;
                        }

                        short[] pcm;
                        try {
                            pcm = decoder.decode(opusPacket);
                        } catch (Exception ignored) {
                            continue;
                        }

                        if (pcm == null || pcm.length == 0) {
                            continue;
                        }

                        writeShortsLE(out, pcm);
                    }
                }
            }

            if (packet.size() > 0) {
                byte[] opusPacket = packet.toByteArray();
                if (!isOpusHeaderPacket(opusPacket)) {
                    short[] pcm = decoder.decode(opusPacket);
                    if (pcm != null && pcm.length > 0) {
                        writeShortsLE(out, pcm);
                    }
                }
            }

            return bytesToShorts(out.toByteArray());
        } finally {
            try {
                decoder.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isWav(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] head = new byte[12];
            if (readFully(in, head) < 12) return false;
            return head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E';
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isOgg(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] head = new byte[4];
            if (readFully(in, head) < 4) return false;
            return head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S';
        } catch (Exception e) {
            return false;
        }
    }

    private static OggPage readOggPage(InputStream in) throws IOException {
        byte[] header = new byte[27];
        int read = readFully(in, header);
        if (read == -1) return null;
        if (read < 27) throw new EOFException("Unexpected EOF while reading Ogg header");

        if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
            throw new IOException("Invalid Ogg stream");
        }

        int segmentCount = header[26] & 0xFF;
        byte[] lacing = new byte[segmentCount];
        if (segmentCount > 0) {
            int lacingRead = readFully(in, lacing);
            if (lacingRead < segmentCount) throw new EOFException("Unexpected EOF while reading Ogg lacing table");
        }

        int dataLen = 0;
        int[] segmentLengths = new int[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            int len = lacing[i] & 0xFF;
            segmentLengths[i] = len;
            dataLen += len;
        }

        byte[] data = new byte[dataLen];
        if (dataLen > 0) {
            int dataRead = readFully(in, data);
            if (dataRead < dataLen) throw new EOFException("Unexpected EOF while reading Ogg page data");
        }

        return new OggPage(segmentLengths, data);
    }

    private static boolean isOpusHeaderPacket(byte[] packet) {
        return startsWith(packet, "OpusHead") || startsWith(packet, "OpusTags");
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data == null) return false;
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (data.length < p.length) return false;

        for (int i = 0; i < p.length; i++) {
            if (data[i] != p[i]) return false;
        }

        return true;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int r = in.read(buf, offset, buf.length - offset);
            if (r == -1) {
                return offset == 0 ? -1 : offset;
            }
            offset += r;
        }
        return offset;
    }

    private static void writeShortsLE(ByteArrayOutputStream out, short[] pcm) {
        ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : pcm) {
            buffer.putShort(s);
        }
        out.writeBytes(buffer.array());
    }

    private static short[] bytesToShorts(byte[] data) {
        if (data == null || data.length == 0) return new short[0];
        int len = data.length / 2;
        short[] s = new short[len];
        ByteBuffer.wrap(data, 0, len * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }

    private static String stripExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static short applyVolume(short s, int vol) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s * vol / 100));
    }

    private static class PCMFrameSupplier implements java.util.function.Supplier<short[]> {

        private final short[] pcm;
        private final int volume;
        private int index = 0;

        PCMFrameSupplier(short[] pcm, int volume) {
            this.pcm = pcm;
            this.volume = volume;
        }

        @Override
        public short[] get() {
            short[] frame = new short[FRAME_SIZE];

            for (int i = 0; i < FRAME_SIZE && index < pcm.length; i++) {
                frame[i] = applyVolume(pcm[index++], volume);
            }

            return frame;
        }
    }

    public record ActiveSound(String name, long startTime, AudioPlayer player, int volume) {
        public void stop() {
            try {
                player.stopPlaying();
            } catch (Exception ignored) {
            }
        }
    }

    public static class SoundSessionManager {

        private static final Map<UUID, List<ActiveSound>> ACTIVE = new ConcurrentHashMap<>();

        public static void add(UUID player, ActiveSound sound) {
            ACTIVE.computeIfAbsent(player, k -> Collections.synchronizedList(new ArrayList<>())).add(sound);
        }

        public static List<ActiveSound> get(UUID player) {
            return ACTIVE.getOrDefault(player, Collections.emptyList());
        }

        public static void stopAll(UUID player) {
            List<ActiveSound> list = ACTIVE.remove(player);
            if (list == null) return;

            for (ActiveSound s : list) {
                try {
                    s.player().stopPlaying();
                } catch (Exception ignored) {
                }
            }
        }

        public static void stop(UUID player, String name) {
            List<ActiveSound> list = ACTIVE.get(player);
            if (list == null) return;

            synchronized (list) {
                Iterator<ActiveSound> it = list.iterator();

                while (it.hasNext()) {
                    ActiveSound s = it.next();

                    if (s.name().equalsIgnoreCase(stripExtension(name))) {
                        try {
                            s.player().stopPlaying();
                        } catch (Exception ignored) {
                        }
                        it.remove();
                    }
                }

                if (list.isEmpty()) {
                    ACTIVE.remove(player);
                }
            }
        }
    }

    private static final class OggPage {
        final int[] segmentLengths;
        final byte[] data;

        private OggPage(int[] segmentLengths, byte[] data) {
            this.segmentLengths = segmentLengths;
            this.data = data;
        }
    }
}