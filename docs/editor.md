# In-game YAML editor

Andreaitemmaker ships a full in-game editor for its content files, so items, weapons, armor,
food, blocks and furniture can be created, inspected, validated and saved without hand-editing
YAML.

```
/aitem editor
```

The editor is a **frontend for the existing configuration format**: it reads and writes the same
files in `plugins/Andreaitemmaker/items|blocks|furniture/`. There is no second format and no
parallel content system — everything you create in the GUI is a normal YAML file that the normal
loader reads, and `/aitem reload` / restarts behave exactly as before.

## Commands

| Command | What it does |
| --- | --- |
| `/aitem editor` | Open the editor hub |
| `/aitem editor create <ITEM\|WEAPON\|ARMOR\|FOOD\|BLOCK\|FURNITURE>` | Start a new entry of that type |
| `/aitem editor edit <id>` | Open an existing entry by id |
| `/aitem editor search <query>` | List matching content across every category |
| `/aitem editor reload` | The normal `/aitem reload` |

Permission: **`andreaitemmaker.editor`** (default `op`). `andreaitemmaker.admin` also grants it.
Without permission the command answers with the usual permission error, and the editor is
in-game only (the console cannot open a GUI).

## The hub

| Button | Purpose |
| --- | --- |
| Items / Weapons / Armor / Food / Blocks / Furniture | List that category (with counts) |
| Search | Find content by id or display name across all categories |
| Create new content | Pick a type, choose an id, then edit every field |
| Validate all content | Re-checks every file on disk and lists problems with the exact field |
| Settings & diagnostics | Effective settings, pack status, pack controls |
| Reload content | Same as `/aitem reload` |

In a content list:

* **Left click** — edit
* **Right click** — inspect (id, type, file, material, base block, whether it is currently loaded)
* **Shift + left click** — duplicate (prompts for a new id)
* **Shift + right click** — delete (asks for confirmation; a copy is kept in `backups/editor/deleted`)

Lists are paginated and searchable, and the folder is scanned **once** per session — the index is
cached and only rebuilt after a save, a delete or a reload.

## Editing an entry

The entry screen shows every field of that content type, one icon per field, grouped in the order
`id → display → gameplay → cosmetics → mechanics`:

| Field kind | Control |
| --- | --- |
| Text | Type it in chat |
| Coloured text | Type it in chat, `&` codes previewed |
| Lore | Add / edit / remove one line at a time |
| Number | Type it in chat, checked against the loader's range |
| Boolean | Click to toggle |
| Material | Paginated picker (any item) + a chat fallback for an exact name |
| Base block | Picker limited to full solid blocks the loader accepts, already-used ones marked |
| Sound | Type it in chat, validated against the Bukkit sound list |
| Texture | Pattern / colour / second colour / outline, a plain hex colour, or a `.png` |
| 3D model / worn armor texture | Path inside `assets/`, checked for `.json`/`.png` and safety |
| Attributes | Only the attribute names the loader accepts, with numeric values |
| Enchantments | Only real enchantment names, with levels |
| Mechanics | Toggle any registered mechanic; edit its parameters |

The toolbar offers **Undo**, **Redo**, **Save**, **Validate**, **Give a preview item**,
**Duplicate**, **Revert to the saved file** and, when the file uses keys the editor does not manage,
**Preserved fields**.

### Mechanics

Any registered mechanic (built-in or from another plugin) can be enabled with one click. Its
parameters are edited through a generic map editor: existing keys show their values, left click
edits, right click removes, and `Add parameter` takes a `key=value` pair. Nested sections and lists
of maps (for example `armor-effects` → `effects`) open their own level, so arbitrary depth works
without any editor change. Values keep their type (a number stays a number, a boolean stays a
boolean) so the loader reads them the same way it always did.

## Text input

Minecraft inventories cannot take free text, so a field that needs typing closes the GUI, asks in
chat and captures your next message:

```
[Editor] Type the display name for new_sword.
[Editor] '&' color codes work, e.g. &bStorm Blade
[Editor] Type cancel to go back, or clear to remove it.
```

* `cancel` returns to the menu without changing anything.
* `clear` removes an optional field.
* An invalid value is explained and you are asked again — nothing is ever applied silently.
* A pending prompt is dropped after two minutes, and it is also cleared as soon as you navigate
  anywhere in the editor, so a normal chat message can never be swallowed.

## Working copy and unsaved changes

Nothing is written to disk while you edit. Opening an entry loads it into a per-player **working
copy**; the file on disk is untouched until you press **Save**.

Closing the GUI with unsaved changes does not destroy them — you get a dialog:

```
You have unsaved changes
[Save]  [Discard]  [Continue editing]
```

`Revert to the saved file` does the same thing from inside the editor, any time — and the revert
itself can be undone, so it is never a one-way door.

Each player has an isolated session, so two admins can edit two entries (or the same one) at the
same time without interfering.

## Undo history

Every edit is undoable, one step at a time, and you can step back further than a single revert:

* **One step = one thing you did.** A click, a toggled boolean, an accepted chat value or a whole
  nested block such as a mechanic and all of its parameters — each is exactly one step. Internal
  writes (a section plus its keys) are grouped, so undo never appears to "do nothing".
* **Navigation is not recorded.** Opening menus, browsing categories and looking at fields leave no
  history behind.
* **50 steps per entry**, per player. The history lives in your session and is never shared with
  another admin.
* **Redo** walks forward again after an undo. Making a new edit clears the redo steps, exactly like
  a text editor.
* **Saving does not wipe the history**, and undoing a save simply makes the entry dirty again —
  useful when you saved something you were not ready for.
* **Revert is one undoable step**: it reloads the file in place, so you can go back to your unsaved
  work with a single undo.
* The `Undo`/`Redo` buttons on the entry screen show how many steps are available. From a nested
  menu (a mechanic's parameters, a texture) press **Back** once and undo there — the step still
  covers everything the action changed.

The full history is in memory only: it belongs to the open session and is dropped when you close
the entry or log out. Permanent versions are the file itself plus `backups/editor/`.

## Your own fields are preserved

The editor only knows the fields it documents. Anything else in a file — extra keys, nested
values, comments, key order — is kept exactly as it was and written back untouched. A file like

```yaml
# my note
id: magic_sword
material: DIAMOND_SWORD
custom-feature:        # from another plugin
  something: true
future-field: 123
```

survives a full edit/save cycle with the comment, both extra keys and the original order intact.
`Preserved fields` in the entry screen lists those keys so you can confirm they were noticed.

## Validation and saving

Saving runs two layers of checks:

1. **Field validation** — every editable field is checked against the same rules the loader
   applies (id format, material exists, number ranges, base-block rules, texture/model path
   safety, mechanic names, food ranges, furniture offsets, sound names, attribute and enchantment
   names). Each problem names the exact YAML field, for example
   `base-block: base block white_wool is already used by 'other_crate'`.
2. **The real loader** — the candidate is written to a scratch file and parsed by
   `ContentLoader` itself. If the loader would skip the file, the save is refused. The editor can
   therefore never accept something the plugin would reject at load time.

Warnings (unknown mechanic, unreadable model path, non-edible food material, …) are reported but do
not block saving. Errors do — a **Force save** button is available when you know better.

On a successful save the editor:

1. writes a backup of the previous file to `plugins/Andreaitemmaker/backups/editor/`,
2. writes the new file atomically (temp file + move, so a crash cannot truncate it),
3. removes the old file if you renamed the id (otherwise it would keep serving the old entry),
4. requests a normal background reload, so content **and** the resource pack are regenerated.

Files written by the editor live exactly where the loader expects them:
`items/<id>.yml` for items/weapons/armor/food, `blocks/<id>.yml` for blocks and
`furniture/<id>.yml` for furniture.

## Limitations

* **`config.yml` is not edited from the GUI.** The Settings screen shows the effective values and
  provides the pack controls instead; the plugin already migrates `config.yml` automatically when
  an update adds options, so hand-editing stays safe and comment-friendly.
* **Very large content sets.** *Validate all content* and *Give a preview item* run the loader on
  the candidate, which means reading the file; validating the whole set runs off the main thread.
* **Two admins saving the same entry** — the last save wins, and every previous version is in
  `backups/editor/`.
* **The undo history is per open session** (in memory, up to 50 steps). Reopening an entry starts a
  fresh history; older versions of the file are in `backups/editor/`, and every save adds one.
* Deleting a block or furniture definition does not remove blocks/furniture already placed in the
  world; they are cleaned up by the normal removal handling on the next load.

## Related

* [items.md](items.md) — the fields the editor writes, explained
* [mechanics.md](mechanics.md) — every built-in mechanic and its parameters
* [blocks.md](blocks.md) / [furniture.md](furniture.md) — type specific fields
* [commands.md](commands.md) — commands and permissions
* [manual-testing.md](manual-testing.md) — the in-game checklist for the editor
