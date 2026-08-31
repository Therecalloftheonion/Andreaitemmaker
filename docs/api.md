# Developer API

Other plugins use Andreaitemmaker through `AndreaitemmakerAPI`. Add the jar (or the
Maven artifact) to your build and depend on the plugin in `plugin.yml`:

```yaml
depend: [Andreaitemmaker]
```

## Lookup & creation

```java
import com.andreaitemmaker.api.AndreaitemmakerAPI;
import com.andreaitemmaker.api.CustomItem;
import org.bukkit.inventory.ItemStack;

AndreaitemmakerAPI api = AndreaitemmakerAPI.get();

CustomItem sword = api.getCustomItem("my_sword");       // by id
CustomItem held  = api.getCustomItem(playerStack);      // by ItemStack (PDC tag, O(1))
ItemStack stack  = api.createItemStack("my_sword", 1);  // build a stack

api.getCustomItems();        // all items/weapons/armor/food
api.getCustomBlocks();       // all blocks
api.getCustomFurnitures();   // all furniture
```

All lookups are O(1) hashmap lookups; no filesystem access and no config parsing happens
during gameplay.

## Resource pack

```java
api.getResourcePack()
   .generate()              // request async regeneration (coalesced)
   .getUrl()                // current download URL
   .getSha1()               // current pack hash
   .sendTo(player);         // send to a player
```

## Mechanics

```java
api.getMechanicRegistry().register(new ItemMechanic() {
    @Override public String getId() { return "my_mechanic"; }
    @Override public boolean onUse(MechanicContext ctx) { /* ... */ return true; }
});
```

## Events

Cancellable events, fired exactly once and respecting cancellation:

- `CustomItemUseEvent`, `CustomItemHitEvent`, `CustomItemConsumeEvent`
- `CustomBlockPlaceEvent`, `CustomBlockBreakEvent`
- `CustomFurniturePlaceEvent`, `CustomFurnitureBreakEvent`

```java
@EventHandler
public void onBlockBreak(CustomBlockBreakEvent e) {
    if (!e.getPlayer().hasPermission("my.server.allow")) e.setCancelled(true);
}
```

## Protection

Block and furniture placement go through the plugin's `ProtectionService` (vanilla
restrictions + `andreaitemmaker.build` permission + registered providers + optional
WorldGuard hook). API placements follow the same contract — unless you explicitly pass a
bypassing context. If you need another protection plugin honored, register a
`ProtectionProvider`:

```java
api.getProtectionService().registerProvider((player, location) ->
    MyProtection.isDenied(player, location) ? "denied by MyProtection" : null);
```

## Thread rules

- Content lookup, item creation and registry reads are safe from any thread (registries
  are immutable snapshots, swapped atomically on reload).
- Bukkit API calls (events, world changes) must run on the main thread.
- `api.reload()` is asynchronous and coalesced — repeated calls collapse into one run.

## Lifecycle

- `AndreaitemmakerAPI.get()` throws `IllegalStateException` if the plugin is not enabled.
- `AndreaitemmakerAPI.isEnabled()` tells you whether it is available.
- The API instance is released on plugin disable; don't cache it across plugin reloads.
