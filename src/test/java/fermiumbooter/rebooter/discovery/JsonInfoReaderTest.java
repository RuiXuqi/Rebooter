package fermiumbooter.rebooter.discovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonInfoReaderTest {

    @Test
    void stopsReadingAfterTheFirstValidArrayEntry() {
        byte[] prefix = "[{\"modid\":\"first\",\"name\":\"First\",\"version\":\"1.0\"}"
                .getBytes(StandardCharsets.UTF_8);

        ModIdentity identity = JsonInfoReader.firstMod(new ThrowAfterPrefixInputStream(prefix));

        assertEquals(ModIdentity.of("first", "First", "1.0"), identity);
    }

    @Test
    void arraySkipsEntriesWithoutAUsableModId() {
        InputStream input = input("["
                + "{\"modid\":12},"
                + "{\"modid\":\"  \"},"
                + "{\"name\":\"No Id\"},"
                + "{\"modid\":\"chosen\",\"version\":\"2.1\"},"
                + "{\"modid\":\"ignored\"}"
                + "]");

        assertEquals(ModIdentity.of("chosen", null, "2.1"), JsonInfoReader.firstMod(input));
    }

    @Test
    void blankAndNonStringFieldsLeaveTheIdentityUnknown() {
        InputStream input = input("[{\"modid\":\"only_id\",\"name\":\"  \",\"version\":7}]");

        assertEquals(ModIdentity.of("only_id", null, null), JsonInfoReader.firstMod(input));
    }

    @Test
    void rootObjectPrefersDirectModIdOverAnEarlierModList() {
        InputStream input = input("{"
                + "\"modList\":[{\"modid\":\"nested\",\"name\":\"Nested\"}],"
                + "\"modid\":\"direct\","
                + "\"name\":\"Direct\","
                + "\"version\":\"3.0\""
                + "}");

        assertEquals(ModIdentity.of("direct", "Direct", "3.0"), JsonInfoReader.firstMod(input));
    }

    @Test
    void rootObjectFallsBackToTheFirstModListEntry() {
        InputStream input = input("{"
                + "\"modListVersion\":2,"
                + "\"modList\":["
                + "{\"modid\":\"nested\",\"name\":\"Nested\",\"version\":\"4.2\"},"
                + "{\"modid\":\"ignored\"}"
                + "]}");

        assertEquals(ModIdentity.of("nested", "Nested", "4.2"), JsonInfoReader.firstMod(input));
    }

    @Test
    void malformedDocumentHasNoIdentity() {
        assertNull(JsonInfoReader.firstMod(input("[{not valid json]")));
    }

    private static InputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
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
