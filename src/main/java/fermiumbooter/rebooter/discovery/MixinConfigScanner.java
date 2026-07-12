package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;

final class MixinConfigScanner {
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final byte[] MIXIN_CONFIG_BYTES = ConfigReader.MIXIN_CONFIG.getBytes(StandardCharsets.US_ASCII);
    private final byte[] utf8Buffer = new byte[MIXIN_CONFIG_BYTES.length];
    private byte[] readBuffer = new byte[8192];
    private InputStream input;
    private int offset;
    private int limit;

    @VisibleForTesting
    boolean mightContainMixinConfig(InputStream input) throws IOException {
        this.input = input;
        this.offset = 0;
        this.limit = 0;
        try {
            if (this.readInt() != CLASS_MAGIC) {
                return false;
            }
            this.skipFully(4);
            int constantPoolCount = this.readUnsignedShort();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = this.readUnsignedByte();
                switch (tag) {
                    case 1:
                        int length = this.readUnsignedShort();
                        if (length == MIXIN_CONFIG_BYTES.length) {
                            this.readFully(this.utf8Buffer, length);
                            if (this.matchesMixinConfigDescriptor()) {
                                return true;
                            }
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
                        return false;
                }
            }
            return false;
        } finally {
            this.input = null;
        }
    }

    ConfigReader.Result scanIfPresent(InputStream input) throws IOException {
        if (!this.mightContainMixinConfig(input)) {
            return null;
        }
        InputStream completeClass = new SequenceInputStream(
                new ByteArrayInputStream(this.readBuffer, 0, this.limit), input);
        return ConfigReader.scan(completeClass);
    }

    private boolean matchesMixinConfigDescriptor() {
        for (int index = 0; index < MIXIN_CONFIG_BYTES.length; index++) {
            if (this.utf8Buffer[index] != MIXIN_CONFIG_BYTES[index]) {
                return false;
            }
        }
        return true;
    }

    private int readUnsignedByte() throws IOException {
        if (this.offset == this.limit) {
            this.fill();
        }
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
            if (this.offset == this.limit) {
                this.fill();
            }
            int copied = Math.min(length - destinationOffset, this.limit - this.offset);
            System.arraycopy(this.readBuffer, this.offset, destination, destinationOffset, copied);
            this.offset += copied;
            destinationOffset += copied;
        }
    }

    private void skipFully(int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            if (this.offset == this.limit) {
                this.fill();
            }
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
            read = this.input.read(this.readBuffer, this.limit, this.readBuffer.length - this.limit);
        } while (read == 0);
        if (read < 0) {
            throw new IOException("Unexpected end of class file");
        }
        this.limit += read;
    }
}
