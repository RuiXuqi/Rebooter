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

    @Nullable
    static String firstModId(InputStream input) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            JsonToken root = reader.peek();
            if (root == JsonToken.BEGIN_ARRAY) return firstModId(reader, SearchMode.STOP_AFTER_MATCH);
            if (root == JsonToken.BEGIN_OBJECT) return firstModId(reader);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private static String firstModId(JsonReader reader) throws IOException {
        String nestedModId = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("modid".equals(name)) {
                String directModId = readModId(reader);
                if (directModId != null) return directModId;
            } else if ("modList".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                String candidate = firstModId(reader, SearchMode.CONSUME_CONTAINER);
                if (nestedModId == null) nestedModId = candidate;
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return nestedModId;
    }

    @Nullable
    private static String firstModId(JsonReader reader, SearchMode searchMode) throws IOException {
        String firstModId = null;
        reader.beginArray();
        while (reader.hasNext()) {
            String candidate;
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                candidate = modId(reader, searchMode);
            } else {
                reader.skipValue();
                candidate = null;
            }
            if (firstModId == null) firstModId = candidate;
            if (searchMode == SearchMode.STOP_AFTER_MATCH && candidate != null) return candidate;
        }
        reader.endArray();
        return firstModId;
    }

    @Nullable
    private static String modId(JsonReader reader, SearchMode searchMode) throws IOException {
        String firstModId = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("modid".equals(name)) {
                String candidate = readModId(reader);
                if (firstModId == null) firstModId = candidate;
                if (searchMode == SearchMode.STOP_AFTER_MATCH && candidate != null) return candidate;
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return firstModId;
    }

    @Nullable
    private static String readModId(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            reader.skipValue();
            return null;
        }
        String modId = reader.nextString().trim();
        return modId.isEmpty() ? null : modId;
    }

    private enum SearchMode {
        STOP_AFTER_MATCH,
        CONSUME_CONTAINER
    }
}
