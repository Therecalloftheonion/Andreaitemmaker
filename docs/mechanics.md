# Mechanics

Mechanics attach behaviors to any item — all config-driven, no code required.

| Mechanic | When | Options |
| --- | --- | --- |
| `heal` | right-click | `amount`, `cooldown` |
| `feed` | right-click | `amount`, `saturation`, `cooldown` |
| `effect` | right-click | `effects: [{type, duration, amplifier, ambient, particles}]`, `cooldown` |
| `launch` | right-click | `power`, `cooldown` |
| `sound` | right-click | `sound`, `volume`, `pitch` |
| `lightning` | hit entity | `damage` (0 = visual only) |
| `ignite` | hit entity | `seconds` |
| `knockback` | hit entity | `power` |
| `armor-effects` | while worn (armor) | `effects:` same format as `effect` |

## Example

```yaml
mechanics:
  heal:
    amount: 2
    cooldown: 3
  lightning:
    damage: 4.0
  effect:
    cooldown: 10
    effects:
      - type: SPEED
        duration: 10
        amplifier: 1
        ambient: true
```

## Cooldowns

Per-player cooldowns apply per mechanic. The PlaceholderAPI placeholders
`%andreaitemmaker_cooldown_<id>_<mechanic>%` expose the remaining seconds.

## Custom mechanics (developer API)

Other plugins can register their own mechanics:

```java
import com.andreaitemmaker.api.*;

MechanicRegistry registry = AndreaitemmakerAPI.get().getMechanicRegistry();
registry.register(new ItemMechanic() {
    @Override public String getId() { return "my_mechanic"; }
    @Override public boolean onUse(MechanicContext ctx) {
        ctx.getPlayer().sendMessage("Used!");
        return true;
    }
});
```

Then server owners use it from YAML:

```yaml
mechanics:
  my_mechanic:
    some: option
```

Unknown mechanics are reported at load so typos are caught immediately. An exception in
one mechanic never crashes the others or the plugin state.
