package fermiumbooter.rebooter.discovery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class JsonInfoReader {

    private JsonInfoReader() {
    }

    @Nullable
    static String firstModId(InputStream input) {
        try {
            JsonElement root = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8));
            if (root.isJsonArray()) return firstModId(root.getAsJsonArray());
            if (!root.isJsonObject()) return null;
            JsonObject object = root.getAsJsonObject();
            String direct = modId(object);
            if (direct != null) return direct;
            JsonElement modList = object.get("modList");
            return modList != null && modList.isJsonArray() ? firstModId(modList.getAsJsonArray()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String firstModId(JsonArray mods) {
        for (JsonElement mod : mods) {
            if (!mod.isJsonObject()) continue;
            String modId = modId(mod.getAsJsonObject());
            if (modId != null) return modId;
        }
        return null;
    }

    @Nullable
    private static String modId(JsonObject mod) {
        JsonElement value = mod.get("modid");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        String modId = value.getAsString().trim();
        return modId.isEmpty() ? null : modId;
    }
}
