package com.andreaitemmaker;

import com.andreaitemmaker.util.Json;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void writesValidObjects() {
        String json = Json.obj("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", "itemmaker:item/foo"));
        JsonParser.parseString(json); // must parse
        assertTrue(json.contains("\"layer0\":\"itemmaker:item/foo\""));
    }

    @Test
    void writesArraysAndNested() {
        String json = Json.obj("overrides", List.of(
                Map.of("predicate", Map.of("custom_model_data", 1001), "model", "itemmaker:item/foo")));
        JsonParser.parseString(json);
        assertTrue(json.contains("\"custom_model_data\":1001"));
    }

    @Test
    void escapesStrings() {
        String json = Json.obj("description", "line1\nline2 \"quoted\" \\ path");
        JsonParser.parseString(json);
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\""));
    }

    @Test
    void writesNumbersAndBooleans() {
        String json = Json.obj("format", 46, "required", true, "none", null);
        assertEquals("{\"format\":46,\"required\":true,\"none\":null}", json);
    }
}
