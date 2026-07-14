package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ClassAnnotationScanner {
    private static final int SCANNER_VERSION = 1;
    private static final int READ_CHUNK_SIZE = 1024;
    static final int MIXIN_CONFIG = 1;
    static final int FORGE_MOD = 1 << 1;
    static final String FORGE_MOD_DESCRIPTOR = "Lnet/minecraftforge/fml/common/Mod;";
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final byte[] MIXIN_CONFIG_BYTES = ConfigReader.MIXIN_CONFIG.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FORGE_MOD_BYTES = FORGE_MOD_DESCRIPTOR.getBytes(StandardCharsets.US_ASCII);
    private final byte[] utf8Buffer = new byte[Math.max(MIXIN_CONFIG_BYTES.length, FORGE_MOD_BYTES.length)];
    private byte[] readBuffer = new byte[8192];
    private InputStream input;
    private int offset;
    private int limit;

    static String cacheProfile() {
        return "class-annotation-scanner-v" + SCANNER_VERSION + '\n'
                + ConfigReader.MIXIN_CONFIG + '\n'
                + FORGE_MOD_DESCRIPTOR + '\n';
    }

    ScanResult scan(InputStream input, long classSize) throws IOException {
        this.input = input;
        this.offset = 0;
        this.limit = 0;
        try {
            if (this.readInt() != CLASS_MAGIC) {
                return ScanResult.empty();
            }
            this.skipFully(4);
            int flags = 0;
            int constantPoolCount = this.readUnsignedShort();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = this.readUnsignedByte();
                switch (tag) {
                    case 1:
                        int length = this.readUnsignedShort();
                        if (length == MIXIN_CONFIG_BYTES.length || length == FORGE_MOD_BYTES.length) {
                            this.readFully(this.utf8Buffer, length);
                            if (matches(this.utf8Buffer, length, MIXIN_CONFIG_BYTES)) flags |= MIXIN_CONFIG;
                            if (matches(this.utf8Buffer, length, FORGE_MOD_BYTES)) flags |= FORGE_MOD;
                        } else {
                            this.skipFully(length);
                        }
                        break;
                    case 3:
                    case 4:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        this.skipFully(4);
                        break;
                    case 5:
                    case 6:
                        this.skipFully(8);
                        index++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        this.skipFully(2);
                        break;
                    case 15:
                        this.skipFully(3);
                        break;
                    default:
                        return ScanResult.empty();
                }
            }
            if (flags == 0) return ScanResult.empty();
            long fullReadStarted = System.nanoTime();
            byte[] classBytes = this.completeClass(input, classSize);
            return new ScanResult(flags, classBytes, System.nanoTime() - fullReadStarted);
        } finally {
            this.input = null;
        }
    }

    private byte[] completeClass(InputStream remaining, long classSize) throws IOException {
        if (classSize > Integer.MAX_VALUE) {
            throw new IOException("Class file is too large");
        }
        if (classSize >= this.limit) {
            byte[] bytes = new byte[(int) classSize];
            System.arraycopy(this.readBuffer, 0, bytes, 0, this.limit);
            int offset = this.limit;
            while (offset < bytes.length) {
                int read = remaining.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new IOException("Unexpected end of class file");
                if (read > 0) offset += read;
            }
            return bytes;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(this.limit + 4096);
        bytes.write(this.readBuffer, 0, this.limit);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = remaining.read(buffer)) >= 0) {
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static boolean matches(byte[] actual, int actualLength, byte[] expected) {
        if (actualLength != expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) return false;
        }
        return true;
    }

    private int readUnsignedByte() throws IOException {
        if (this.offset == this.limit) this.fill();
        return this.readBuffer[this.offset++] & 0xFF;
    }

    private int readUnsignedShort() throws IOException {
        return (this.readUnsignedByte() << 8) | this.readUnsignedByte();
    }

    private int readInt() throws IOException {
        return (this.readUnsignedByte() << 24)
                | (this.readUnsignedByte() << 16)
                | (this.readUnsignedByte() << 8)
                | this.readUnsignedByte();
    }

    private void readFully(byte[] destination, int length) throws IOException {
        int destinationOffset = 0;
        while (destinationOffset < length) {
            if (this.offset == this.limit) this.fill();
            int copied = Math.min(length - destinationOffset, this.limit - this.offset);
            System.arraycopy(this.readBuffer, this.offset, destination, destinationOffset, copied);
            this.offset += copied;
            destinationOffset += copied;
        }
    }

    private void skipFully(int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            if (this.offset == this.limit) this.fill();
            int skipped = Math.min(remaining, this.limit - this.offset);
            this.offset += skipped;
            remaining -= skipped;
        }
    }

    private void fill() throws IOException {
        if (this.limit == this.readBuffer.length) {
            byte[] expanded = new byte[this.readBuffer.length << 1];
            System.arraycopy(this.readBuffer, 0, expanded, 0, this.readBuffer.length);
            this.readBuffer = expanded;
        }
        int read;
        do {
            read = this.input.read(
                    this.readBuffer,
                    this.limit,
                    Math.min(READ_CHUNK_SIZE, this.readBuffer.length - this.limit));
        } while (read == 0);
        if (read < 0) throw new IOException("Unexpected end of class file");
        this.limit += read;
    }

    static final class ScanResult {
        private static final ScanResult EMPTY = new ScanResult(0, null, 0L);
        private final int flags;
        private final byte[] classBytes;
        private final long fullClassReadNanos;

        private ScanResult(int flags, byte[] classBytes, long fullClassReadNanos) {
            this.flags = flags;
            this.classBytes = classBytes;
            this.fullClassReadNanos = fullClassReadNanos;
        }

        private static ScanResult empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return this.flags == 0;
        }

        int flags() {
            return this.flags;
        }

        byte[] classBytes() {
            return this.classBytes;
        }

        long fullClassReadNanos() {
            return this.fullClassReadNanos;
        }

        @VisibleForTesting
        boolean has(int flag) {
            return (this.flags & flag) != 0;
        }
    }

    @VisibleForTesting
    ScanResult scan(InputStream input) throws IOException {
        return this.scan(input, -1);
    }
}
