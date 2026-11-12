package net.dvmn2.logicplugin.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Primitive readers and writers of the editor wire format. The layout matches Minecraft's
 * {@code PacketByteBuf}: VarInt lengths, UTF-8 strings, UUIDs as two big-endian longs.
 *
 * <p>Stateless and thread-safe.
 */
public final class Wire {

    private static final int VARINT_MAX_BYTES = 5;
    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    private Wire() {
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int rest = value;
        while ((rest & ~SEGMENT_BITS) != 0) {
            out.writeByte((rest & SEGMENT_BITS) | CONTINUE_BIT);
            rest >>>= 7;
        }
        out.writeByte(rest);
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < VARINT_MAX_BYTES; i++) {
            int read = in.readByte();
            value |= (read & SEGMENT_BITS) << (7 * i);
            if ((read & CONTINUE_BIT) == 0) {
                return value;
            }
        }
        throw new IOException("VarInt is too long");
    }

    public static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        writeBytes(out, value.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        writeVarInt(out, value.length);
        out.write(value);
    }

    /** Reads a VarInt-prefixed byte array and rejects lengths above {@code maxLength}. */
    public static byte[] readBytes(DataInputStream in, int maxLength) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > maxLength) {
            throw new IOException("Array length out of range: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}
