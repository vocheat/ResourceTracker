package net.fabricmc.resourcetracker.importing;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The named, big endian NBT used by litematic files, without version-specific game APIs. */
final class BoundedNbtReader {
    static final long MAX_BYTES = 256L * 1024 * 1024;
    private final DataInputStream input;
    private int tags;
    private long allocationBudget = MAX_BYTES;

    private BoundedNbtReader(InputStream input) {
        this.input = new DataInputStream(new FilterInputStream(input) {
            private long remaining = MAX_BYTES;
            @Override public int read() throws IOException {
                checkInterrupted();
                int value = super.read();
                if (value >= 0 && --remaining < 0) throw new IOException("NBT exceeds 256 MiB decompressed limit");
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                checkInterrupted();
                int count = super.read(bytes, offset, (int) Math.min(length, remaining + 1));
                if (count > 0 && (remaining -= count) < 0) throw new IOException("NBT exceeds 256 MiB decompressed limit");
                return count;
            }
        });
    }

    static Map<String, Object> read(InputStream input) throws IOException {
        BoundedNbtReader reader = new BoundedNbtReader(input);
        if (reader.input.readUnsignedByte() != 10) throw new IOException("NBT root must be a compound");
        reader.utf();
        Map<String, Object> root = reader.compound(0);
        if (reader.input.read() != -1) throw new IOException("Unexpected data after NBT root");
        return root;
    }

    private Object value(int type, int depth) throws IOException {
        checkInterrupted();
        if (depth > 64 || ++tags > 1_000_000) throw new IOException("NBT nesting or tag count limit exceeded");
        reserve(16);
        return switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 7 -> { int length = length(1); reserve(length); byte[] data = new byte[length]; input.readFully(data); yield data; }
            case 8 -> utf();
            case 9 -> {
                int elementType = input.readUnsignedByte();
                int length = length(8);
                if (elementType > 12 || (length > 0 && elementType == 0)) throw new IOException("Invalid NBT list type");
                reserve(24L + (long) length * 8);
                List<Object> list = new ArrayList<>(Math.min(length, 4096));
                for (int i = 0; i < length; i++) list.add(value(elementType, depth + 1));
                yield list;
            }
            case 10 -> compound(depth);
            case 11 -> { int length = length(4); reserve((long) length * 4); int[] data = new int[length]; for (int i = 0; i < data.length; i++) data[i] = input.readInt(); yield data; }
            case 12 -> { int length = length(8); reserve((long) length * 8); long[] data = new long[length]; for (int i = 0; i < data.length; i++) data[i] = input.readLong(); yield data; }
            default -> throw new IOException("Invalid NBT tag type: " + type);
        };
    }

    private int length(int width) throws IOException {
        int length = input.readInt();
        if (length < 0 || (long) length * width > MAX_BYTES) throw new IOException("Invalid or excessive NBT array length");
        return length;
    }

    private Map<String, Object> compound(int depth) throws IOException {
        checkInterrupted();
        reserve(64);
        Map<String, Object> result = new LinkedHashMap<>();
        int type;
        while ((type = input.readUnsignedByte()) != 0) {
            reserve(64);
            String name = utf();
            if (result.containsKey(name)) throw new IOException("Duplicate NBT tag: " + name);
            result.put(name, value(type, depth + 1));
        }
        return result;
    }

    private String utf() throws IOException {
        String value = input.readUTF();
        reserve(40L + (long) value.length() * 2);
        return value;
    }

    private void reserve(long bytes) throws IOException {
        if ((allocationBudget -= bytes) < 0) throw new IOException("NBT allocation exceeds 256 MiB limit");
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
    }
}
