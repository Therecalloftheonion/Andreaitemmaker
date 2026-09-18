# Manual testing checklist

These tests must be performed **by the repository owner on a real server** before
claiming production readiness. None of them have been run by automation.The server owner didnt however test them like he should

## Setup

1. Fresh install: drop the jar in `plugins/`, start the server, confirm
   `plugins/Andreaitemmaker/` is created with `config.yml`, example content and `assets/`
   folders.
2. `/aitem reload` completes without errors; log shows concise counts
   (`Loaded N items, M blocks, K furniture definitions in Xms`).
3. `/aitem diagnose` shows the plugin version, server version, Java version, content
   counts, pack status/hash/size and generation duration.

## Items

4. `/aitem give <id>` gives the item with correct name, lore and texture.
5. Using the item triggers its mechanics (right-click and hit mechanics).
6. Food restores hunger/saturation and respects cooldown.
7. Weapons deal configured damage.

## Armor

8. Equipping a piece applies `armor-effects` while worn and removes them when unequipped.
9. A HEAD armor piece with a `model:` renders its 3D model on the head (1.21.2+).
10. Chest/legs/feet pieces render the 2D `armor-texture:` layer (1.21.2+).

## Custom blocks

11. Place a custom block; the correct appearance replaces the base block.
12. The block keeps its identity across server restart, plugin reload and chunk
    unload/reload, in multiple worlds.
13. Breaking it drops the custom item (when `drops-item: true`) and cleans the id.
14. Explosion: with `explosion-protected: true` the block survives; with `false` it is
    destroyed and the id cleaned up.
15. A piston cannot push/pull a custom block.
16. Fire does not leave a stale id behind.
17. Replacing a custom block with a different material via an external editor is detected
    and the stale tag cleaned up.

## Furniture

18. Place furniture; the model renders on an invisible armor stand.
19. Right-click to pick it back up.
20. Furniture survives restart/reload; normal armor stands are unaffected.
21. Removing a furniture definition cleans up its entities.

## Protection

22. With WorldGuard installed, placing blocks/furniture in a protected region is denied
    (world unchanged, item not consumed).
23. Denying the `andreaitemmaker.build` permission blocks placement.
24. Vanilla placement restrictions still apply.

## Resource pack

25. A joining player receives the pack prompt and the content is visible after accepting.
26. A player who fails to download is re-sent the pack on the next join.
27. A player who successfully loads is not re-prompted unnecessarily.
28. `/aitem pack regenerate` re-sends the new pack to online players; `pack.url` shows the
    right URL.
29. With the firewall blocking port 8163, hosting `pack/` (unzipped folder) at a fixed URL
    via `pack.public-url` works.
30. Unchanged config regenerates the same hash; changing any content changes the hash.
31. `/aitem reload` twice in quick succession runs one combined generation (no crash, no
    stale state).

## In-game editor

35. `/aitem editor` opens for an operator; a non-operator without `andreaitemmaker.editor`
    gets a permission error. The console is told the editor is in-game only.
36. Categories list the right entries and pagination works with more entries than fit a page.
37. Search finds entries by id and by display name across categories.
38. Create a `WEAPON`: enter an id, set the material, display name, lore, attributes,
    enchantments, a texture and the `lightning` mechanic from the GUI.
39. Press **Save**: the file appears in `items/<id>.yml`, the log reports the content
    reloading, and `/aitem give <id>` gives the item with the configured values.
40. **Unsaved changes**: edit something and press Escape. The Save/Discard/Continue dialog
    appears; Discard leaves the file untouched, Continue editing returns to the editor, and
    closing the dialog returns to the editor rather than losing the changes.
41. **Revert**: edit a field, press *Revert to the saved file*, confirm the field shows the
    value from disk again — and that one *Undo* brings the unsaved edit back.
42. **Text input**: a field prompt appears in chat; `cancel` returns without changes, `clear`
    removes an optional field, an invalid value is refused with a message and re-prompted, and
    a normal chat message after closing the editor is **not** swallowed.
43. **Validation**: remove the `material` line from a file by hand, open it in the editor and
    press Validate — an error naming `material` is shown and saving is refused until fixed.
44. **Duplicate**: duplicate an entry; a new id is written with the same values, and for a
    block the base block must be chosen again before it saves.
45. **Delete**: delete an entry; the file disappears, the copy in `backups/editor/deleted`
    exists, and the content reloads.
46. **Unknown fields**: add a custom key (and a comment) to a file, edit and save it in the
    editor — the key, its nested values, the comment and the original key order are all still
    there afterwards. *Preserved fields* lists the key.
47. **Blocks**: create a block, place it, break it, restart the server and confirm the
    persisted block identity still works.
48. **Furniture**: create furniture with sounds and an `offset-y`, place it, pick it up, and
    confirm the sounds and the placement height are right.
49. **Mechanics**: enable `armor-effects`, add an `effects` entry through the list editor, save,
    and confirm the effects apply while the piece is worn.
50. **Preview**: press *Give a preview item* with unsaved changes and confirm the item in your
    inventory matches the values in the GUI (not the file on disk).
51. **Rename**: change an entry's `id` and save; the old file must be gone and only the new id
    must be loaded.
52. **Two players**: two operators editing at the same time do not see each other's fields, and
    both can save without errors.
53. **Ranges that need a live server**: a `base-block` of glass/stairs/slabs is rejected and
    already-used base blocks are marked in the picker.
54. **Reload while editing**: `/aitem reload` while an editor session has unsaved changes does
    not crash and the working copy is still intact afterwards.

## Undo history

55. Make five different edits (a boolean, a number, a text value, a picked material and a
    mechanic parameter). The *Undo* button shows `5`; undo five times and the entry matches the
    file again (the header stops showing unsaved changes).
56. Change one mechanic's parameters (several values) and undo once — the **whole** mechanic
    change must disappear in one step, not one value at a time.
57. Browsing menus, opening fields and pressing Back must not add undo steps (*Undo* count stays).
58. Undo twice, then make a new edit — *Redo* must be empty afterwards.
59. Save an entry, then undo: the entry becomes unsaved again and the file on disk is
    unchanged until you save again.
60. With more than 50 edits, the history stops growing (no lag, no unbounded memory) and undo
    still works.

## Regression

61. Vanilla blocks and items behave completely normally.
62. Large content sets (hundreds of items) load and generate without errors.
63. Multiplayer: two players see the same custom content after both load the pack.
