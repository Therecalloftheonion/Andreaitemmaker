package com.andreaitemmaker.placeholder;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.content.ContentRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Optional PlaceholderAPI integration (soft-depend; the plugin works without it).
 *
 * <p>Registered only when PlaceholderAPI is present. Placeholders under the
 * {@code andreaitemmaker} identifier:
 * <ul>
 *   <li>{@code %andreaitemmaker_has_item_<id>%} — {@code yes}/{@code no}, the player has the item in their inventory</li>
 *   <li>{@code %andreaitemmaker_amount_<id>%} — total count of that item in the player's inventory</li>
 *   <li>{@code %andreaitemmaker_holding_<id>%} — {@code yes}/{@code no}, the item is in the player's main hand</li>
 *   <li>{@code %andreaitemmaker_cooldown_<id>_<mechanic>%} — remaining cooldown seconds for that mechanic (0 = ready)</li>
 *   <li>{@code %andreaitemmaker_content_count%} and per-type counts — total content entries</li>
 * </ul>
 */
public final class AndreaitemmakerExpansion extends PlaceholderExpansion {

    /** The placeholder action after {@code %andreaitemmaker_}. */
    enum Action {
        HAS_ITEM, AMOUNT, HOLDING, COOLDOWN,
        CONTENT_COUNT, ITEM_COUNT, WEAPON_COUNT, ARMOR_COUNT, FOOD_COUNT, BLOCK_COUNT, FURNITURE_COUNT
    }

    /** Parsed placeholder params: action plus optional content id and mechanic id. */
    record Parsed(Action action, String id, String mechanic) {
    }

    private final AndreaitemmakerPlugin plugin;

    public AndreaitemmakerExpansion(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "andreaitemmaker";
    }

    @Override
    public String getAuthor() {
        return "Andreaitemmaker";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getRequiredPlugin() {
        return "Andreaitemmaker";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Parsed parsed = parse(params);
        if (parsed == null) {
            return null; // unknown placeholder: leave it untouched
        }
        ContentRegistry registry = plugin.getContentRegistry();
        return switch (parsed.action()) {
            case CONTENT_COUNT -> String.valueOf(registry.getAll().size());
            case ITEM_COUNT -> String.valueOf(registry.getItems().size());
            case WEAPON_COUNT -> String.valueOf(registry.getWeapons().size());
            case ARMOR_COUNT -> String.valueOf(registry.getArmor().size());
            case FOOD_COUNT -> String.valueOf(registry.getFood().size());
            case BLOCK_COUNT -> String.valueOf(registry.getBlocks().size());
            case FURNITURE_COUNT -> String.valueOf(registry.getFurnitures().size());
            case HAS_ITEM -> {
                if (parsed.id().isEmpty() || registry.getItem(parsed.id()) == null) {
                    yield "no";
                }
                yield countInInventory(player, registry, parsed.id()) > 0 ? "yes" : "no";
            }
            case AMOUNT -> {
                if (parsed.id().isEmpty() || registry.getItem(parsed.id()) == null) {
                    yield "0";
                }
                yield String.valueOf(countInInventory(player, registry, parsed.id()));
            }
            case HOLDING -> {
                if (!(player instanceof Player online) || parsed.id().isEmpty()
                        || registry.getItem(parsed.id()) == null) {
                    yield "no";
                }
                CustomItem held = registry.getItemByStack(online.getInventory().getItemInMainHand());
                yield held != null && held.getId().equals(parsed.id()) ? "yes" : "no";
            }
            case COOLDOWN -> {
                if (!(player instanceof Player online)) {
                    yield "0"; // cooldowns are runtime-only
                }
                yield String.valueOf(MechanicContext.cooldownRemaining(online, parsed.id(), parsed.mechanic()));
            }
        };
    }

    private static int countInInventory(OfflinePlayer player, ContentRegistry registry, String id) {
        if (!(player instanceof Player online)) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : online.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            CustomItem item = registry.getItemByStack(stack);
            if (item != null && item.getId().equals(id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Parse placeholder params into an action (pure, unit-tested). Null when unknown. */
    static Parsed parse(String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        String p = params.toLowerCase(Locale.ROOT);
        switch (p) {
            case "content_count" -> {
                return new Parsed(Action.CONTENT_COUNT, null, null);
            }
            case "item_count" -> {
                return new Parsed(Action.ITEM_COUNT, null, null);
            }
            case "weapon_count" -> {
                return new Parsed(Action.WEAPON_COUNT, null, null);
            }
            case "armor_count" -> {
                return new Parsed(Action.ARMOR_COUNT, null, null);
            }
            case "food_count" -> {
                return new Parsed(Action.FOOD_COUNT, null, null);
            }
            case "block_count" -> {
                return new Parsed(Action.BLOCK_COUNT, null, null);
            }
            case "furniture_count" -> {
                return new Parsed(Action.FURNITURE_COUNT, null, null);
            }
            default -> {
                // fall through to prefix parsing below
            }
        }
        if (p.startsWith("has_item_")) {
            return idOnly(new Parsed(Action.HAS_ITEM, p.substring("has_item_".length()), null));
        }
        if (p.startsWith("amount_")) {
            return idOnly(new Parsed(Action.AMOUNT, p.substring("amount_".length()), null));
        }
        if (p.startsWith("holding_")) {
            return idOnly(new Parsed(Action.HOLDING, p.substring("holding_".length()), null));
        }
        if (p.startsWith("cooldown_")) {
            // Item ids and mechanic ids can both contain underscores; the mechanic is
            // always last, so split on the final underscore.
            String rest = p.substring("cooldown_".length());
            int split = rest.lastIndexOf('_');
            if (split <= 0 || split == rest.length() - 1) {
                return null;
            }
            return new Parsed(Action.COOLDOWN, rest.substring(0, split), rest.substring(split + 1));
        }
        return null;
    }

    private static Parsed idOnly(Parsed parsed) {
        return parsed.id().isEmpty() ? null : parsed;
    }
}
