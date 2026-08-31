package com.andreaitemmaker.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Converts Blockbench "Bedrock Edition" item/block models to the Java Edition model format
 * on the fly, so server owners can drag-and-drop models exported from Blockbench without
 * re-exporting them as Java. Conversion is conservative: it only rewrites what the Java
 * client rejects and leaves every other field untouched.
 *
 * <p>A model is treated as Bedrock when it carries {@code format_version}, {@code groups},
 * {@code mcmodels} or numeric texture keys ({@code "0"}, {@code "1"}, ...) — none of which
 * exist in Java models. Anything else passes through unchanged.
 */
public final class BedrockModelConverter {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final JsonParser PARSER = new JsonParser();
    private static final double ROTATION_STEP = 22.5;

    private BedrockModelConverter() {
    }

    /**
     * Returns {@code json} converted to the Java model format when it is a Bedrock model,
     * otherwise the input unchanged. Never throws: on any parse problem the original text
     * is returned so the caller's existing validation/fallback path applies.
     */
    public static String convert(String json, String modelName, Logger logger) {
        JsonObject root;
        try {
            JsonElement el = PARSER.parse(json);
            if (!el.isJsonObject()) {
                return json;
            }
            root = el.getAsJsonObject();
        } catch (JsonParseException e) {
            return json;
        }
        if (!isBedrock(root)) {
            return json;
        }
        try {
            stripBedrockFields(root);
            renumberTextureKeys(root);
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements != null) {
                for (JsonElement e : elements) {
                    fixElement(e);
                }
            }
            logger.info("Converted Bedrock-format model '" + modelName + "' to Java format automatically");
            return GSON.toJson(root);
        } catch (RuntimeException | LinkageError e) {
            // Never let a conversion problem (or a missing Gson on an exotic server) take
            // down pack generation: fall back to the original model.
            logger.warning("Could not convert Bedrock model '" + modelName + "': " + e
                    + " — using it as-is");
            return json;
        }
    }

    private static boolean isBedrock(JsonObject root) {
        if (root.has("format_version") || root.has("groups") || root.has("mcmodels")) {
            return true;
        }
        JsonObject textures = root.getAsJsonObject("textures");
        if (textures != null && !textures.isEmpty()) {
            for (String key : textures.keySet()) {
                if (key.matches("\\d+")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Remove top-level keys that exist only in the Bedrock format. */
    private static void stripBedrockFields(JsonObject root) {
        root.remove("format_version");
        root.remove("credit");
        root.remove("groups");
        root.remove("mcmodels");
        root.remove("bone_visibility");
    }

    /** Rename numeric texture keys ({@code "0"} -> {@code "layer0"}, ...) as Java expects. */
    private static void renumberTextureKeys(JsonObject root) {
        JsonObject textures = root.getAsJsonObject("textures");
        if (textures == null || textures.isEmpty()) {
            return;
        }
        JsonObject fixed = new JsonObject();
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            String key = e.getKey();
            if (key.matches("\\d+")) {
                key = "layer" + key;
            }
            fixed.add(key, e.getValue());
        }
        root.add("textures", fixed);
    }

    /**
     * Make a single element valid for Java:
     * <ul>
     *   <li>{@code from}/{@code to} must be ordered (Blockbench Bedrock can emit {@code from > to});</li>
     *   <li>the rotation angle must be a multiple of 22.5&deg; (Java throws otherwise);
     *       valid angles are left untouched.</li>
     * </ul>
     */
    private static void fixElement(JsonElement el) {
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject e = el.getAsJsonObject();
        JsonArray from = e.getAsJsonArray("from");
        JsonArray to = e.getAsJsonArray("to");
        if (from != null && to != null && from.size() == 3 && to.size() == 3) {
            JsonArray fixedFrom = new JsonArray();
            JsonArray fixedTo = new JsonArray();
            boolean swapped = false;
            for (int i = 0; i < 3; i++) {
                double a = from.get(i).getAsDouble();
                double b = to.get(i).getAsDouble();
                if (a > b) {
                    double t = a;
                    a = b;
                    b = t;
                    swapped = true;
                }
                fixedFrom.add(a);
                fixedTo.add(b);
            }
            if (swapped) {
                e.add("from", fixedFrom);
                e.add("to", fixedTo);
            }
        }
        JsonObject rotation = e.getAsJsonObject("rotation");
        if (rotation != null && rotation.has("angle")) {
            try {
                double angle = rotation.get("angle").getAsDouble();
                double rounded = Math.round(angle / ROTATION_STEP) * ROTATION_STEP;
                if (Math.abs(rounded - angle) > 0.001) {
                    rotation.addProperty("angle", rounded);
                }
            } catch (NumberFormatException ignore) {
                // non-numeric angle; leave it and let the client decide
            }
        }
    }
}
