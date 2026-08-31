package com.andreaitemmaker.pack;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.Json;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern layout (1.21.2+): every item gets its own {@code assets/<ns>/items/<id>.json}
 * definition and stacks carry the {@code minecraft:item_model} component, so any base
 * material works. Armor additionally gets an equipment asset; since 1.21.2 the armor
 * layer textures live under {@code textures/entity/equipment/...} and the asset uses
 * object-form layers ({@code [{"texture": ns:id}]}).
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
        // 1.21.2+ resolves layer textures as assets/<ns>/textures/entity/equipment/<layerType>/<id>.png.
        PackGenerator.put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid/" + id + ".png", layer1);
        PackGenerator.put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid_leggings/" + id + ".png", layer2);
        // Object-form layers: [{"texture": "ns:id"}]. Both layer types are always present:
        // the client renders worn armor exclusively from these 2D layers (verified in the
        // 1.21.4/1.21.5 client renderer) — there is no 3D-on-body fallback to omit into.
        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("humanoid", List.of(Map.of("texture", ns + ":" + id)));
        layers.put("humanoid_leggings", List.of(Map.of("texture", ns + ":" + id)));
        // 1.21.2-1.21.3: equipment models live under models/equipment/; 1.21.4+ moved them up to equipment/.
        String dir = ctx.target().format() >= 46 ? "equipment" : "models/equipment";
        PackGenerator.putString(entries, "assets/" + ns + "/" + dir + "/" + id + ".json",
                Json.obj("layers", layers));
    }
}
