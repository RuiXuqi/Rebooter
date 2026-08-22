package fermiumbooter.rebooter.discovery;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class JsonInfoReader {

    private JsonInfoReader() {
    }

    /**
     * Reads the identity of the first mod entry declared in an {@code mcmod.info} document.
     * A root object is described by its own {@code modid} when it has one and by the first entry of
     * its {@code modList} otherwise; a root array is described by its first entry carrying a
     * {@code modid}, whose siblings are never read.
     */
    @Nullable
    static ModIdentity firstMod(InputStream input) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            JsonToken root = reader.peek();
            if (root == JsonToken.BEGIN_ARRAY) return firstArrayEntry(reader, false);
            if (root == JsonToken.BEGIN_OBJECT) return rootObjectEntry(reader);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private static ModIdentity rootObjectEntry(JsonReader reader) throws IOException {
        EntryBuilder direct = new EntryBuilder();
        ModIdentity nested = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (direct.accepts(name)) {
                direct.read(name, reader);
            } else if ("modList".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                ModIdentity candidate = firstArrayEntry(reader, true);
                if (nested == null) nested = candidate;
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        ModIdentity directIdentity = direct.build();
        return directIdentity == null ? nested : directIdentity;
    }

    /**
     * @param consumeContainer whether the array must be read to its end even after a match, which is
     *                         required when the caller keeps reading the enclosing object
     */
    @Nullable
    private static ModIdentity firstArrayEntry(JsonReader reader, boolean consumeContainer) throws IOException {
        ModIdentity firstEntry = null;
        reader.beginArray();
        while (reader.hasNext()) {
            if (firstEntry != null || reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            firstEntry = objectEntry(reader);
            if (firstEntry != null && !consumeContainer) return firstEntry;
        }
        reader.endArray();
        return firstEntry;
    }

    @Nullable
    private static ModIdentity objectEntry(JsonReader reader) throws IOException {
        EntryBuilder entry = new EntryBuilder();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (entry.accepts(name)) {
                entry.read(name, reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return entry.build();
    }

    @Nullable
    private static String readText(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            reader.skipValue();
            return null;
        }
        String text = reader.nextString().trim();
        return text.isEmpty() ? null : text;
    }

    private static final class EntryBuilder {
        private String modId;
        private String name;
        private String version;

        private boolean accepts(String field) {
            return "modid".equals(field) || "name".equals(field) || "version".equals(field);
        }

        private void read(String field, JsonReader reader) throws IOException {
            String value = readText(reader);
            if ("modid".equals(field)) {
                if (this.modId == null) this.modId = value;
            } else if ("name".equals(field)) {
                if (this.name == null) this.name = value;
            } else if (this.version == null) {
                this.version = value;
            }
        }

        @Nullable
        private ModIdentity build() {
            return this.modId == null ? null : ModIdentity.of(this.modId, this.name, this.version);
        }
    }
}
