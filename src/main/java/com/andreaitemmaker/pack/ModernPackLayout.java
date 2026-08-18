package com.andreaitemmaker.pack;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.Json;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Modern layout (1.21.2+): every item gets its own {@code assets/<ns>/items/<id>.json}
 * definition and stacks carry the {@code minecraft:item_model} component, so any base
 * material works. Armor additionally gets an equipment asset; since 1.21.11 (pack format
 * 75) the armor layer textures moved under {@code textures/entity/equipment/...}.
 */
final class ModernPackLayout implements PackLayout {

    ModernPackLayout() {
    }

    @Override
    public boolean isModern() {
        return true;
    }

    @Override
    public void writeItemDefinition(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                    CustomItem item, Map<Material, List<CustomItem>> legacyGroups) {
        PackGenerator.putString(entries, "assets/" + ctx.namespace() + "/items/" + item.getId() + ".json",
                Json.obj("model", Map.of("type", "minecraft:model", "model", ctx.namespace() + ":item/" + item.getId())));
    }

    @Override
    public void writeArmorAssets(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                 CustomItem item, byte[] layer1, byte[] layer2) {
        String ns = ctx.namespace();
        String id = item.getId();
        if (ctx.target().format() >= 75) {
            // 1.21.11+: equipment textures live under textures/entity/equipment/...
            PackGenerator.put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid/" + id + ".png", layer1);
            PackGenerator.put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid_leggings/" + id + ".png", layer2);
            PackGenerator.putString(entries, "assets/" + ns + "/equipment/" + id + ".json", Json.obj("layers", Map.of(
                    "humanoid", List.of(Map.of("texture", ns + ":" + id)),
                    "humanoid_leggings", List.of(Map.of("texture", ns + ":" + id)))));
        } else {
            PackGenerator.putString(entries, "assets/" + ns + "/equipment/" + id + ".json", Json.obj("layers", Map.of(
                    "humanoid", List.of(ns + ":" + id),
                    "humanoid_leggings", List.of(ns + ":" + id))));
        }
    }
}
