package fermiumbooter.rebooter.discovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonInfoReaderTest {

    @Test
    void stopsReadingAfterTheFirstValidArrayModId() {
        byte[] prefix = "[{\"modid\":\"first\"}".getBytes(StandardCharsets.UTF_8);

        String modId = JsonInfoReader.firstModId(new ThrowAfterPrefixInputStream(prefix));

        assertEquals("first", modId);
    }

    @Test
    void rootObjectPrefersDirectModIdOverAnEarlierModList() {
        InputStream input = new ByteArrayInputStream(("{"
                + "\"modList\":[{\"modid\":\"nested\"}],"
                + "\"modid\":\"direct\""
                + "}").getBytes(StandardCharsets.UTF_8));

        assertEquals("direct", JsonInfoReader.firstModId(input));
    }

    private static final class ThrowAfterPrefixInputStream extends InputStream {
        private final byte[] prefix;
        private int offset;

        private ThrowAfterPrefixInputStream(byte[] prefix) {
            this.prefix = prefix;
        }

        @Override
        public int read() throws IOException {
            if (this.offset >= this.prefix.length) throw new IOException("metadata tail must not be read");
            return this.prefix[this.offset++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (this.offset >= this.prefix.length) throw new IOException("metadata tail must not be read");
            buffer[offset] = this.prefix[this.offset++];
            return 1;
        }
    }
}
