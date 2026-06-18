package de.jakomi1.voiceServer.utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OpusFileUtils {

    private OpusFileUtils() {}

    private static final byte[] OGG_MAGIC = new byte[]{'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD_MAGIC = "OpusHead".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OPUS_TAGS_MAGIC = "OpusTags".getBytes(StandardCharsets.US_ASCII);

    private static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final int DEFAULT_CHANNELS = 1;
    private static final int DEFAULT_PRE_SKIP = 312;

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int j = 0; j < 8; j++) {
                if ((r & 0x80000000) != 0) {
                    r = (r << 1) ^ 0x04C11DB7;
                } else {
                    r <<= 1;
                }
            }
            CRC_TABLE[i] = r;
        }
    }

    public static void writeOggOpus(File file, List<byte[]> packets) throws IOException {
        if (file == null) throw new IOException("Target file is null");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            int serial = ThreadLocalRandom.current().nextInt();
            int seq = 0;
            long granulePosition = 0L;

            writePage(out, buildOpusHeadPacket(), serial, seq++, 0L, 0x02);
            writePage(out, buildOpusTagsPacket(), serial, seq++, 0L, 0x00);

            if (packets != null) {
                for (int i = 0; i < packets.size(); i++) {
                    byte[] packet = packets.get(i);
                    if (packet == null || packet.length == 0) continue;

                    granulePosition += 960L;

                    int headerType = 0x00;
                    if (i == packets.size() - 1) {
                        headerType |= 0x04;
                    }

                    writePage(out, packet, serial, seq++, granulePosition, headerType);
                }
            }
        }
    }

    public static List<byte[]> readOggOpusPackets(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException(String.valueOf(file));
        }

        byte[] data = Files.readAllBytes(file.toPath());
        List<byte[]> packets = new ArrayList<>();

        ByteArrayOutputStream currentPacket = new ByteArrayOutputStream();
        int pos = 0;

        while (pos + 27 <= data.length) {
            if (!matches(data, pos, OGG_MAGIC)) {
                throw new IOException("Invalid OGG magic at position " + pos);
            }

            int pageSegments = data[pos + 26] & 0xFF;
            int headerLen = 27 + pageSegments;
            if (pos + headerLen > data.length) break;

            int pageDataLen = 0;
            for (int i = 0; i < pageSegments; i++) {
                pageDataLen += data[pos + 27 + i] & 0xFF;
            }
            if (pos + headerLen + pageDataLen > data.length) break;

            int dataPos = pos + headerLen;

            for (int i = 0; i < pageSegments; i++) {
                int segLen = data[pos + 27 + i] & 0xFF;

                if (segLen > 0) {
                    currentPacket.write(data, dataPos, segLen);
                    dataPos += segLen;
                }

                if (segLen < 255) {
                    byte[] packet = currentPacket.toByteArray();
                    currentPacket.reset();

                    if (packet.length > 0 && !isHeaderPacket(packet)) {
                        packets.add(packet);
                    }
                }
            }

            pos += headerLen + pageDataLen;
        }

        return packets;
    }

    public static short[] decodeOggOpusToPcm(File file, de.maxhenkel.voicechat.api.opus.OpusDecoder decoder) throws Exception {
        if (decoder == null) {
            throw new IllegalArgumentException("decoder == null");
        }

        List<byte[]> packets = readOggOpusPackets(file);
        ByteArrayOutputStream pcmBytes = new ByteArrayOutputStream();

        for (byte[] packet : packets) {
            if (packet == null || packet.length == 0) continue;

            short[] decoded = decoder.decode(packet);
            if (decoded == null || decoded.length == 0) continue;

            ByteBuffer buffer = ByteBuffer.allocate(decoded.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : decoded) {
                buffer.putShort(s);
            }
            pcmBytes.write(buffer.array());
        }

        byte[] raw = pcmBytes.toByteArray();
        short[] out = new short[raw.length / 2];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    private static void writePage(OutputStream out,
                                  byte[] packet,
                                  int serial,
                                  int seq,
                                  long granulePosition,
                                  int headerType) throws IOException {

        byte[] lacing = buildLacingTable(packet.length);

        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(OGG_MAGIC);
        page.write(0); // version
        page.write(headerType);
        writeLongLE(page, granulePosition);
        writeIntLE(page, serial);
        writeIntLE(page, seq);
        writeIntLE(page, 0); // checksum placeholder
        page.write(lacing.length);
        page.write(lacing);
        page.write(packet);

        byte[] pageBytes = page.toByteArray();
        int crc = oggCrc(pageBytes);
        putIntLE(pageBytes, 22, crc);

        out.write(pageBytes);
    }

    private static byte[] buildOpusHeadPacket() throws IOException {
        ByteBuffer b = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        b.put(OPUS_HEAD_MAGIC);
        b.put((byte) 1); // version
        b.put((byte) DEFAULT_CHANNELS);
        b.putShort((short) DEFAULT_PRE_SKIP);
        b.putInt(DEFAULT_SAMPLE_RATE);
        b.putShort((short) 0); // output gain
        b.put((byte) 0); // channel mapping family
        return b.array();
    }

    private static byte[] buildOpusTagsPacket() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(OPUS_TAGS_MAGIC);

        byte[] vendor = "VoiceServer".getBytes(StandardCharsets.US_ASCII);
        writeIntLE(out, vendor.length);
        out.write(vendor);

        writeIntLE(out, 0); // user comment list length
        return out.toByteArray();
    }

    private static byte[] buildLacingTable(int packetLength) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int remaining = packetLength;

        while (remaining >= 255) {
            out.write(255);
            remaining -= 255;
        }

        if (remaining > 0) {
            out.write(remaining);
        } else {
            out.write(0);
        }

        return out.toByteArray();
    }

    private static boolean isHeaderPacket(byte[] packet) {
        return startsWith(packet, OPUS_HEAD_MAGIC) || startsWith(packet, OPUS_TAGS_MAGIC);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || prefix == null || data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean matches(byte[] data, int offset, byte[] magic) {
        if (data.length - offset < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) return false;
        }
        return true;
    }

    private static int oggCrc(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) & 0xFF) ^ (b & 0xFF)];
        }
        return crc;
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeLongLE(OutputStream out, long v) throws IOException {
        out.write((int) (v) & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 24) & 0xFF);
        out.write((int) (v >>> 32) & 0xFF);
        out.write((int) (v >>> 40) & 0xFF);
        out.write((int) (v >>> 48) & 0xFF);
        out.write((int) (v >>> 56) & 0xFF);
    }

    private static void putIntLE(byte[] arr, int offset, int v) {
        arr[offset] = (byte) (v & 0xFF);
        arr[offset + 1] = (byte) ((v >>> 8) & 0xFF);
        arr[offset + 2] = (byte) ((v >>> 16) & 0xFF);
        arr[offset + 3] = (byte) ((v >>> 24) & 0xFF);
    }
}