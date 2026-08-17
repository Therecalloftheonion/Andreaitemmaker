package com.andreaitemmaker.api;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * Main entry point of the Andreaitemmaker developer API. Other plugins obtain it with
 * {@code AndreaitemmakerAPI.get()} after Andreaitemmaker has enabled.
 *
 * <pre>{@code
 * AndreaitemmakerAPI api = AndreaitemmakerAPI.get();
 * CustomItem sword = api.getCustomItem("my_sword");
 * ItemStack stack = api.createItemStack("my_sword", 1);
 * }</pre>
 */
public final class AndreaitemmakerAPI {

    private static AndreaitemmakerAPI instance;

    private final AndreaitemmakerPlugin plugin;

    private AndreaitemmakerAPI(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    /** Create the API instance (called by the plugin on enable). */
    public static void init(AndreaitemmakerPlugin plugin) {
        instance = new AndreaitemmakerAPI(plugin);
    }

    /** Release the API instance (called by the plugin on disable). */
    public static void close() {
        instance = null;
    }

    /** Whether the API is available (plugin enabled). */
    public static boolean isEnabled() {
        return instance != null;
    }

    /**
     * Get the API.
     *
     * @throws IllegalStateException when Andreaitemmaker is not enabled
     */
    public static AndreaitemmakerAPI get() {
        if (instance == null) {
            throw new IllegalStateException("Andreaitemmaker is not enabled yet");
        }
        return instance;
    }

    /** The Andreaitemmaker plugin instance. */
    public Plugin getPlugin() {
        return plugin;
    }

    /** Look up a custom item (or weapon/armor/food) by id. */
    public CustomItem getCustomItem(String id) {
        return plugin.getContentRegistry().getItem(id);
    }

    /** Resolve the custom item an ItemStack belongs to (by persistent data), or null. */
    public CustomItem getCustomItem(ItemStack stack) {
        return plugin.getContentRegistry().getItemByStack(stack);
    }

    public Collection<CustomItem> getCustomItems() {
        return plugin.getContentRegistry().getItems();
    }

    public CustomBlock getCustomBlock(String id) {
        return plugin.getContentRegistry().getBlock(id);
    }

    public Collection<CustomBlock> getCustomBlocks() {
        return plugin.getContentRegistry().getBlocks();
    }

    public CustomFurniture getCustomFurniture(String id) {
        return plugin.getContentRegistry().getFurniture(id);
    }

    public Collection<CustomFurniture> getCustomFurnitures() {
        return plugin.getContentRegistry().getFurnitures();
    }

    /** Create an ItemStack for a custom item id (1 item). Returns null when unknown. */
    public ItemStack createItemStack(String id) {
        return createItemStack(id, 1);
    }

    /** Create an ItemStack for a custom item id with the given amount. Returns null when unknown. */
    public ItemStack createItemStack(String id, int amount) {
        CustomItem item = getCustomItem(id);
        if (item == null) {
            return null;
        }
        return plugin.getItemFactory().build(item, amount);
    }

    public ResourcePackManager getResourcePack() {
        return plugin.getPackManager();
    }

    public MechanicRegistry getMechanicRegistry() {
        return plugin.getMechanicRegistry();
    }

    /** Reload configuration, content and the resource pack. */
    public void reload() {
        plugin.reloadAll();
    }
}
