package com.andreaitemmaker;

import com.andreaitemmaker.pack.BedrockModelConverter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockModelConverterTest {

    private static final Logger LOG = Logger.getLogger("BedrockModelConverterTest");

    private static final String BEDROCK_MODEL = """
            {
              "format_version": "1.9.0",
              "credit": "Made with Blockbench",
              "texture_size": [64, 64],
              "textures": {"0": "itemmaker:item/helmet", "1": "itemmaker:item/helmet_vfx"},
              "groups": [{"name": "root"}],
              "gui_light": "front",
              "elements": [
                {"name": "part", "from": [8, 4, 8], "to": [4, 12, 12],
                 "rotation": {"angle": 30, "axis": "y", "origin": [8, 0, 8]},
                 "faces": {"north": {"uv": [0, 0, 1, 1], "texture": "#0"}}}
              ]
            }
            """;

    @Test
    void convertsBedrockModelToJava() {
        String out = BedrockModelConverter.convert(BEDROCK_MODEL, "helmet", LOG);
        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        // Bedrock-only top-level fields are gone.
        assertFalse(root.has("format_version"));
        assertFalse(root.has("credit"));
        assertFalse(root.has("groups"));
        // Texture keys renamed 0/1 -> layer0/layer1.
        JsonObject textures = root.getAsJsonObject("textures");
        assertTrue(textures.has("layer0"));
        assertTrue(textures.has("layer1"));
        assertFalse(textures.has("0"));
        // from > to element was swapped.
        JsonArray elements = root.getAsJsonArray("elements");
        JsonObject el = elements.get(0).getAsJsonObject();
        assertEquals(4, el.getAsJsonArray("from").get(0).getAsDouble(), 0.001);
        assertEquals(8, el.getAsJsonArray("to").get(0).getAsDouble(), 0.001);
        // Rotation angle rounded to the nearest multiple of 22.5 (30 -> 22.5).
        assertEquals(22.5, el.getAsJsonObject("rotation").get("angle").getAsDouble(), 0.001);
    }

    @Test
    void leavesJavaModelsUntouched() {
        String java = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"itemmaker:item/sword\"}}";
        String out = BedrockModelConverter.convert(java, "sword", LOG);
        assertEquals(java, out);
    }

    @Test
    void fixesInvertedElementsAndKeepsValidRotations() {
        String model = """
                {"textures": {"layer0": "itemmaker:item/x"},
                 "elements": [
                   {"from": [0, 0, 0], "to": [4, 4, 4]},
                   {"from": [5, 5, 5], "to": [1, 2, 3]}
                 ]}
                """;
        // No Bedrock markers -> must pass through unchanged.
        assertEquals(model, BedrockModelConverter.convert(model, "x", LOG));
    }

    @Test
    void invalidJsonPassesThroughUnchanged() {
        String garbage = "{not json";
        assertEquals(garbage, BedrockModelConverter.convert(garbage, "broken", LOG));
    }

    @Test
    void zeroThicknessAndMultiTextureElementsSurvive() {
        String model = """
                {"format_version": "1.9.0",
                 "textures": {"2": "itemmaker:item/blade"},
                 "elements": [
                   {"from": [8, 4, 6], "to": [8, 20, 10], "faces": {"east": {"uv": [0, 0, 2, 2], "texture": "#2"}}}
                 ]}
                """;
        String out = BedrockModelConverter.convert(model, "sword", LOG);
        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        assertTrue(root.getAsJsonObject("textures").has("layer2"));
        JsonObject el = root.getAsJsonArray("elements").get(0).getAsJsonObject();
        // from == to on the x axis is preserved (zero-thickness elements render in Java).
        assertEquals(8, el.getAsJsonArray("from").get(0).getAsDouble(), 0.001);
        assertEquals(8, el.getAsJsonArray("to").get(0).getAsDouble(), 0.001);
    }
}
