# Litematica Plus LTS: user guide

Mod version **1.0.5**, Minecraft **26.3**, Fabric. Guide updated **24 October 2026**. Button and option names come from the mod's English translation and the defaults from the code; if you have changed your settings, yours may differ.

Русская версия: [USER_GUIDE_RU.md](USER_GUIDE_RU.md).

## Contents

- [Quick start](#quick-start)
- [Installation](#installation)
- [Key ideas](#key-ideas)
- [The mod menu](#the-mod-menu)
- [Controls](#controls)
- [Schematics: the file library](#schematics-the-file-library)
- [Placements](#placements)
- [Layers and X/Z bounds](#layers-and-xz-bounds)
- [Easy place](#easy-place)
- [Printer edition](#printer-edition)
- [Materials](#materials)
- [Block replacements](#block-replacements)
- [Verifying a build](#verifying-a-build)
- [New schematic: saving a build](#new-schematic-saving-a-build)
- [Paste, fill, replace and delete](#paste-fill-replace-and-delete)
- [Zones for team builds](#zones-for-team-builds)
- [Shared server schematics (Syncmatica)](#shared-server-schematics-syncmatica)
- [Add-ons and compatibility](#add-ons-and-compatibility)
- [Settings reference](#settings-reference)
- [Ready-made setups](#ready-made-setups)
- [Files and recovery](#files-and-recovery)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)

## Quick start

### Build from a schematic in 5 minutes

1. Put `litematica-plus-lts-1.0.5.jar` into your `mods` folder together with Fabric API. Remove Litematica, MaLiLib and EasyPlaceFix: this mod replaces them.
2. Start the game, join a world and press **H** to open the mod menu.
3. Open the **Schematics** tab and press **Folder**. Copy a `.litematic`, `.schem`, `.schematic`, `.nbt` or `.bp` file into the folder that opens, then press **Refresh**.
4. Select the file and press **Place at my position**. The schematic appears as a hologram at your feet and the placement editor opens.
5. Move the schematic in the editor: drag with the left mouse button, scroll to change the height, **R** rotates, **M** mirrors, **T** switches to the view from above. You can also type exact coordinates into the X/Y/Z fields. Close the editor with **Done** or **Esc**.
6. Press **H** again → **Settings** → **Easy place** category → turn on **Easy place**.
7. Take the stick out of your main hand (it is the mod's tool). Aim at a see-through ghost block and right-click: the mod picks the right block from your inventory and places it facing the right way. Hold the button and sweep your aim to keep placing.
8. See what is still missing on the **Materials** tab, and what is placed wrong on the **Verifier** tab and by the colored highlights in the world.

You can also build by hand with easy place off: the ghosts show what goes where, and a middle click on a ghost puts the needed block into your hand.

### Save your own build in 2 minutes

1. Hold a stick.
2. **Ctrl + scroll** to the **Area** mode (the mode name shows above the hotbar).
3. Left-click one corner of the build and right-click the opposite corner.
4. **H** → **New schematic** tab, type a file name and press **Save**. The schematic appears on the **Schematics** tab.

### Worth remembering

| Key | Action |
|---|---|
| `H` | Mod menu |
| `F7` | Layers and X/Z bounds |
| Right-click a ghost | Easy place (when on) |
| Middle-click a ghost | Put the needed block into your hand |
| Stick + `Ctrl` + scroll | Switch the tool mode |
| `Ctrl+F` in Settings | Search the settings |

All other mod keys are unbound by default; bind the ones you need under **Settings → Hotkeys**.

## Installation

| Component | Requirement |
|---|---|
| Minecraft | 26.3 (mod metadata: `~26.3`) |
| Java | 25 or newer |
| Fabric Loader | 0.19.3 or newer (built with 0.19.5) |
| Fabric API | 0.161.0+26.3 |
| Mod Menu | Optional: opens the mod's settings from the mod list |
| Sodium, Iris, Voxy, VoxyMap, Jade, Stack Calculator, Inventory Profiles Next, Axiom | Optional integrations, see [Add-ons](#add-ons-and-compatibility) |

The mod is client-only: nothing needs to be installed on the server.

1. Open the folder of the game profile you play. Modrinth App and other launchers give every profile its own folder, which is not necessarily `.minecraft`.
2. Install Fabric Loader for 26.3 and Fabric API.
3. Remove Litematica, MaLiLib and EasyPlaceFix from `mods`: their features are already inside this mod. Keep MaLiLib only if another mod needs it. If Litematica is still there, the mod shows a warning over the title screen at startup with **Open mods folder** and **Continue anyway**, and says so in chat when you join a world: together the two mods catch the same clicks and draw schematics twice.
4. Put **one** of the two files into `mods`:
   - `litematica-plus-lts-1.0.5.jar` — the standard edition;
   - `litematica-plus-lts-1.0.5-printer.jar` — the same mod plus a printer (see [Printer edition](#printer-edition)). Many servers forbid printers, so the standard edition contains no printer code.
5. Start the game and press **H** in a world.

The `schematics/` folder is shared with Litematica: if you used it before, all your schematics are already there.

### Building from source

```powershell
.\gradlew.bat clean build
```

Output: `build/libs/litematica-plus-lts-1.0.5.jar` and `build/libs/litematica-plus-lts-1.0.5-printer.jar`.

## Key ideas

- **Schematic** — a file holding a build (`.litematic` and other formats).
- **Placement** — a schematic put into the world at certain coordinates. One file can be placed any number of times. Removing or renaming a placement never touches the file.
- **Ghost** — a see-through schematic block where the world does not have it yet.
- **Error highlight** — colored outlines on wrong, extra and missing blocks.
- **Sub-region** — a part of a schematic (a `.litematic` can hold several). Sub-regions can be hidden, moved and rotated one by one.
- **Layer** — a slice of the schematic along X, Y or Z. While a layer is set, the mod shows and helps with that slice only.
- **Tool** — the item (a stick by default) that selects areas, moves placements, pastes and deletes.
- **Zone** — a part of a placement that one player takes in a team build.

## The mod menu

Open it with **H** or from Mod Menu. It remembers the last tab and does not pause singleplayer.

| Tab | Purpose |
|---|---|
| Quick Access | Summary of the selected placement: progress, errors, quick switches, current layer |
| Schematics | File library: search, sorting, 3D preview, placing, export, delete |
| Placements | Schematics in the world: coordinates, rotation, mirror, lock, sub-regions, copy grid, paste |
| Zones | Split a placement into parts for team building |
| Server | Shared server schematics through Syncmatica |
| Layers | Layer slices and X/Z bounds (`F7`) |
| New schematic | Select an area and save it to a file |
| Materials | What you need, what you have, where the rest is stored; export |
| Replacements | Swap schematic blocks for others without changing the file |
| Verifier | Building errors near you; a click highlights the spot in the world |
| Settings | All options by category, search, hotkeys |
| Add-ons | State of integrations with other mods and the problem log |

Hover any button or option to see what it does.

## Controls

### Mod hotkeys

Bind keys under **Settings → Hotkeys** or in Minecraft's own Controls menu, category Litematica Plus.

| Action | Default |
|---|---|
| Open Litematica Plus | `H` |
| Open layers and X/Z bounds | `F7` |
| Edit placement in the world (editor) | unbound |
| Toggle schematic rendering | unbound |
| Toggle easy place | unbound |
| Toggle tool item | unbound |
| Next tool mode | unbound |
| Next layer mode | unbound |
| Move layer up / Move layer down | unbound |
| Toggle X/Z bounds | unbound |
| Open the material list | unbound |
| Hide the ghost block you look at | unbound |
| Show all hidden blocks | unbound |
| Toggle hold to keep placing | unbound |
| Toggle excavation guard | unbound |
| Toggle auto tool switch | unbound |
| Toggle breaking wrong blocks | unbound |
| Toggle substitute materials | unbound |
| Toggle ghosts only on errors | unbound |
| Toggle highlight of placeable blocks | unbound |
| Take materials from the open container | unbound |
| Toggle printer (printer edition only) | unbound |

To bind a key:

1. Click the action's row; it shows "press a key...".
2. Press a key or any mouse button except the left one. For a combination, hold `Ctrl`, `Shift` or `Alt`, for example `Ctrl + K`. To bind `Alt` alone, press and release it.
3. `Esc` clears the binding and a left click cancels. A key already in use shows "Also used by: ...".

Combinations with `Ctrl`, `Shift` and `Alt` are caught by the mod and never reach the game: for example, a bound `Ctrl + Q` does not drop a stack. While you type in a text field the mod's keys do nothing. The main key is stored in `options.txt` and the modifiers in the mod's config.

### The tool (stick)

The tool works in the main hand while **Tool item active** is on, and never in spectator mode. Change the item under **Settings → Tools → Tool item**.

Modes cycle: **Area → Move → Paste → Fill → Replace → Delete**.

| Mode | Left click | Right click |
|---|---|---|
| Area | Corner 1 of the selected box | Corner 2 |
| Move | Move the selected placement to the target | Rotate it by 90° |
| Paste | Ask to paste, second click confirms | Cancel / stop |
| Fill | Ask to fill the selection with the chosen block, second click confirms | Cancel / stop |
| Replace | Ask to replace one block with another in the selection, second click confirms | Cancel / stop |
| Delete | Ask to delete the selection, second click confirms | Cancel / stop |

| Input with the tool | Action |
|---|---|
| `Ctrl` + scroll | Switch mode |
| Middle click | Next mode; with `Shift`, previous |
| `Alt` + scroll | Nudge the selection box or placement one block along your view |
| `Shift` + `Alt` + scroll (Area, Fill, Replace, Delete) | Nudge only the first corner |
| `Alt` + click | Use your own position instead of the targeted block |
| `Alt` + middle click (Fill, Replace) | Remember the targeted block: what to fill with / what to replace with |
| `Alt` + `Shift` + middle click (Replace) | Remember what to replace (any state of that block) |

In Area mode a corner goes on the targeted block itself. In Move mode the schematic's origin goes into the neighbouring cell on the side of the face you clicked. With no target, your position is used. The tool reaches up to 160 blocks; this does not change easy place reach.

Without the tool: right-click a ghost or an entity marker for easy place (when on), middle-click for pick block.

### Placement editor

Opens with **Editor** on the Placements tab, after **Place at my position**, or with its hotkey. It is a see-through screen with a panel on the right: the schematic stays visible in the world and moves at once.

| Input | Result |
|---|---|
| Left drag outside the panel | Move the schematic horizontally |
| Click an X/Y/Z field, type, `Enter` | Set a coordinate |
| `Ctrl+V` | Paste three numbers from the clipboard, such as `2000 64 -350` |
| Arrow keys | Nudge horizontally by the step |
| `PgUp` / `PgDn` | Nudge up / down |
| `Shift` + arrows / `PgUp` / `PgDn` | Nudge by 10 steps |
| Step button | Step of 1, 5, 16 or 64 blocks |
| Scroll (normal view) | Placement height |
| `R` / `M` | Rotate by 90° / next mirror |
| `T` | View from above and back |
| Scroll (view from above) | Zoom |
| Right drag (view from above) | Pan the camera |
| `Enter` or `Esc` | Close the editor |

The panel also has **Shown**, **Lock**, **Above**, **Menu** and **Paste**. The view from above moves only the camera: terrain is visible only in loaded chunks.

## Schematics: the file library

Supported formats:

| Format | Read | Write |
|---|---|---|
| `.litematic` (Litematica) | Yes: multiple regions, negative sizes, block entities, entities | Yes — the main save format |
| `.schem` (Sponge v2/v3, WorldEdit, FAWE) | Yes | **To .schem** button |
| `.nbt` (vanilla structure) | Yes | **To .nbt** button |
| `.schematic` (legacy MCEdit and Schematica) | Yes, numeric block IDs are converted to modern blocks | No |
| `.bp` (Axiom blueprint) | Yes, even without Axiom installed | **To .bp** button |

On the **Schematics** tab:

- **Filter by name** searches the current folder; subfolders open in the list and **Up one folder** goes back.
- The sort button switches between name, size (schematic volume), file weight and date; pressing it again reverses the order.
- On the right: size, block and region counts, author, creation date, data version, file size and a **3D preview** built from the real block models and textures. Drag to rotate, right drag to pan, scroll to zoom, double-click to reset; arrow keys and `+`/`-` work from the keyboard. Huge schematics are drawn as simplified cubes.
- **Place at my position** puts the schematic at your feet and opens the editor. Double-clicking a file does the same.
- **To .schem / To .nbt / To .bp** save a copy next to the file (an existing file is never overwritten; `_2` is added), with all regions merged into one volume. With Axiom installed, **To .bp** drops the copy straight into the Axiom blueprint browser.
- **Delete** removes the file from disk after a second press. It cannot be undone.
- **Folder** opens the folder in your file manager, **Refresh** re-reads the list.
- With Axiom installed, the list has an **Axiom blueprints** folder.

Block states from older game versions go through the vanilla DataFixer. Blocks this game version does not know are skipped (not pasted and not verified), and the mod says in chat how many there were.

## Placements

The **Placements** tab lists the schematics in the current world and dimension. The list is kept separately for every server (or singleplayer world folder) and dimension.

| Control | What it does |
|---|---|
| Placement name | Name shown in lists; the file is not changed |
| X / Y / Z | Corner coordinates. Click a number to type; `Shift` + «±» steps by 10 |
| To me | Move the corner to where you stand |
| Turn: 0/90/180/270 | Rotate 90° clockwise |
| Mirror: off / Z / X | Mirroring |
| Shown | A hidden placement is neither drawn nor checked |
| Locked | Prevents accidental moves, rotation and mirroring. It does not protect the world from paste |
| Sub-regions (N) | The sub-region list |
| Grid | Repeat the schematic as copies |
| Editor | The see-through editor over the world |
| Paste | Put the schematic into the world (creative), with a second press |
| Reload | Re-read the file from disk if it changed |
| Remove | Remove the placement (the file stays), with a second press |

Rotation and mirroring turn around the placement's corner, so X/Y/Z are not necessarily the lowest corner of the visible build.

### Sub-regions

Click a sub-region to open its page: **Show sub-region**, X/Y/Z offset, rotation, mirroring and **Put back**. The offset is in the schematic's own axes, before the placement is rotated, so the sub-region turns together with the whole build. Moved sub-regions are marked with an asterisk. For shared Syncmatica schematics the offsets come from the server and are sent back to it.

### Copy grid

**Grid** repeats the schematic along the axes: for each axis, how many copies go in the negative direction (`-X`), in the positive direction (`+X`), and the step. An **auto** step equals the schematic's size on that axis, so copies sit side by side; a custom step leaves gaps or overlaps them. All copies are one placement: ghosts, verification, progress, materials, easy place and paste count them together. The limit is 256 copies and 268 million cells in total. **Remove grid** keeps a single copy.

## Layers and X/Z bounds

Press **F7** or open the **Layers** tab.

| Mode | What is shown |
|---|---|
| All layers | The whole schematic |
| Single layer | Only the given coordinate |
| This and below | Coordinates up to the given one |
| This and above | Coordinates from the given one |
| Range | From "From" to "To", inclusive |

- **Axis** switches X / Y / Z. The Y axis gives horizontal floors; X and Z give vertical slices.
- **To my position** moves the layer to the coordinate of your feet.
- The Move layer up/down keys shift the layer; while all layers are shown they switch to a single layer at your height.

**X/Z bounds** clip the schematic on top of the layer. **Limit by X/Z** turns the clipping on; you can limit X only, Z only or both.

| Button | What it takes |
|---|---|
| Here | Your coordinate for that bound |
| Fit placement | The bounds of the selected placement |
| Fit selection | The X/Z bounds and a Y layer range from the tool's selection box (the combined box when there are several) |
| Reset | Removes the X/Z limits and keeps the layer mode |

Ghosts, error highlights, easy place, pick block and Jade work only inside the layer and the bounds. With **Count only the visible part** on, progress, counters and the material list count only that part too, and paste places only that part. Bounds are kept per world.

## Easy place

A right-click on a ghost places the needed block with the right rotation and state. It has the EasyPlaceFix feature set built in: the mod tries look directions and click points, simulates the placement and picks the one that gives the schematic state. The needed turn is sent to the server while your camera stays still. Turn it on under **Settings → Easy place → Easy place** (off by default).

### When it works

- **Show schematics** and **Easy place** are on; the placement is enabled and loaded.
- The cell is inside the current layer and X/Z bounds.
- You can build (not spectator), no screen is open, and the tool is not in your main hand.
- Ghosts beyond normal interaction reach are not placed.
- In survival, the needed item is in the 36 main inventory slots. Take items out of shulker boxes first: Materials and the info panel show them after a "+" (`12+52` means 12 at hand, 52 packed).
- The block has support. A ghost that cannot stand where it is right now (snow, a torch, a carpet without support) is skipped, and the ray goes on to the support behind it.
- Renamed items and items with contents are not used unless **Use items with data** is on.

The item is taken from the hotbar or moved from the inventory into the current slot. While you fly with an elytra, easy place does not catch right-clicks (fireworks work as usual).

### Pace

| Pace | Pause between placements |
|---|---|
| Balanced | 2 ticks (≈0.1 s) |
| Safe | 4 ticks — for strict anti-cheats |
| Fast | 1 tick |
| Custom | "Custom delay", 0–20 ticks |

**Random extra tick** adds 0 or 1 tick. This is the base pause; waiting for the turn, the server's reply and follow-up clicks come on top.

### Rotation and orientation

- **Turn for the server** — the server gets the needed look direction while the camera stays still.
- **Turn lead, ticks** (0–3) — how many ticks before the click the turn is sent. For blocks that face along `FACING` the mod still waits at least 2 ticks.
- **Restore look after click** — sends your real look direction right after the click.
- **Only exact orientation** — skip a block when no click gives the schematic orientation.
- **Hidden-face and air clicks** — needed for blocks without support; some anti-cheats reject them.

If the server rejects placements: use the **Safe** pace, turn off air clicks, stand closer, raise the turn lead.

### Finishing states

With **Finish block states** the mod makes extra clicks after placing: repeater delay, comparator mode, lever, open trapdoors, doors and gates, double slabs, candles, snow layers, a flower in a pot, a candle on a cake, an eye in an end portal frame, respawn anchor charge, note pitch (note blocks have a click queue with a progress line on the HUD). Sign text (the front side, plain text) comes from the schematic, and crafter slots are disabled as saved.

- **Light and put out candles and campfires** — flint and steel or a fire charge lights them, a shovel puts out a campfire, an empty hand puts out a candle.
- **Waterlog blocks with a bucket** (off) — a water bucket fills, an empty bucket drains. A missed aim can pour water next to the block.
- **Observers wait** (off) — an observer is placed only after the block it watches.

A state the world holds (a door kept open by redstone, for example) cannot be changed by the mod.

### Schematic entities

With **Place schematic entities**, item frames and glow item frames (with their item and rotation), paintings and armor stands (with armor) get a marker. Aim at the marker and use. Materials for entities are not part of the material list.

### Functional blocks and containers

With **Open containers as usual**, a click on a chest, furnace, crafting table or another block with a menu opens it, even with a ghost in front of it; `Shift` + click places the ghost.

### Stray block protection

- **No stray blocks** (on) — while easy place is on, a block in hand cannot be placed by hand inside a placement or within 2 blocks of it, and the held button no longer places a second block next to a ghost you just filled.
- **Placement guard** (off) — the same without easy place: you cannot place by hand a block the schematic does not expect.
- In both cases shulker boxes, chests, ender chests, barrels, crafting tables, furnaces, anvils and other workstations may go where the schematic leaves air.

### Breaking

- **Excavation guard** (off) — while easy place is on, stops you from breaking blocks **outside the schematic** (outside every shown schematic; functional blocks can still be broken) and **correct blocks** (blocks that already match). **Say why** explains it above the hotbar.
- **Do not spoil schematic blocks with tools** (on) — cancels a right-click with a shovel, hoe or axe when it would turn the block into something the schematic does not ask for (grass into a path, dirt into farmland, a log into a stripped log).
- **Auto tool switch** (off) — before a block inside an enabled placement starts breaking, takes the best tool. Works without easy place too.
- **Break wrong blocks** (off) — a different block, or the right block facing the wrong way, is mined and placed again, one block at a time. Containers, workstations, ender chests, lecterns, jukeboxes, beacons, spawners, liquids and unbreakable blocks are never touched. Extra blocks where the schematic wants air are yours to clear.
- **Swap ground variants** (off) — dirt, grass, coarse dirt or a path standing where the schematic has another of them is dug out and replaced (dirt and grass are left alone while they are interchangeable).

### Items

- **Locked slots** — mark hotbar slots 1–9 (click or press 1–9). Easy place, pick block and auto tool switch only select them and never put items into them, so your tools stay where they are. Slots locked in Inventory Profiles Next are respected too.
- **Substitute materials** (off) — when the exact block is missing, places another gate, trapdoor or coral fan of the same kind, or a block from your substitute list. **Add held block** adds the block in your main hand to the list (up to 256), **Clear list** empties it. Walls are never swapped for other walls. The verifier still compares with the original schematic, so a substituted block may show as an error; for a lasting swap use the [Replacements](#block-replacements) tab.
- **Explain refusals** (on) — shows above the hotbar why a block was not placed (item missing, item only with data, turn to face the block and so on).

## Printer edition

`litematica-plus-lts-1.0.5-printer.jar` is the same mod with the same id plus a printer. The printer places missing schematic blocks around you by itself, without clicking, using the easy place settings: pace, turn for the server, finishing states, breaking wrong blocks, substitutes, layers and bounds.

- Turn it on under **Settings → Printer → Printer** or with the "Toggle printer" key (unbound by default).
- **Printer reach** — 1–6 blocks from your eyes, 4 by default; it never reaches past the game's own reach.
- Printer settings are saved right away to `config/litematicaplus-printer.json`.

Before using it on a server, make sure printers are allowed there.

## Materials

Select a placement and open **Materials**.

| Column | Meaning |
|---|---|
| Item | The item (stacks show as `130 (2 st + 2)`) |
| Total | How many the whole volume (or the visible part) needs |
| Needed | How many are not yet placed correctly |
| Have | How many you carry; after a "+", packed in shulker boxes or stored in the ender chest |

Counting handles double slabs, candles, snow layers, sea pickles and fluid buckets; a potted flower is a pot plus the flower, a candle cake is a cake plus a candle; upper door halves and bed heads are not counted twice. A block that is there but turned the wrong way or in another state needs no material (except slabs, candles and layers, where the state is the count).

- **Sort**: missing, total, name, not in inventory.
- **Hide done** — hide materials you already have enough of.
- **Hide ghosts** — hide the ghosts of the selected material in the world, in every schematic (verification and counters stay the same); press again to show them. The "Hide the ghost block you look at" and "Show all hidden blocks" keys do the same. The choice is kept per world and server.
- **Click a row** to light up, through walls, the remembered containers that hold that material (needs **Remember opened containers**).
- **Export**: Excel `.xlsx` (Summary, To gather and All materials sheets), CSV, Markdown, JSON or **Copy**. Files go to `litematica-plus/materials/`, and chat shows a link to the file and the folder. Copy takes the visible rows, files take the whole list.

The **ender chest** is added to Have once you open it after joining (**Count the ender chest in materials**).

**Container memory**: the mod remembers what is in the chests, barrels and shulker boxes you open in this world during this session. Remembered containers within 64 blocks that hold a material you still lack in your inventory get an orange outline on their own (**Highlight containers with missing materials**).

### Container helper

Open a chest, barrel, shulker box, ender chest, chest minecart or chest boat, dispenser, hopper, donkey, mule or llama:

| Highlight | Meaning |
|---|---|
| Green | A needed item, loose |
| Yellow | A shulker box with needed items inside |
| Light blue | Slots of the item in the row under the cursor |

A **material panel** sits beside the container: what is still missing, how much of it is here and how much you carry.

- Click a row to take the needed amount of that item; `Shift` + click takes all of it from here; right-click sends the amount to Stack Calculator.
- **Take needed** (or the "Take materials from the open container" key) takes exactly the shortfall. A stack is split with a few clicks when needed, and a shulker box is taken whole only when loose items are not enough.
- **Put back extra** returns materials beyond what is needed. The item in your hand and slots locked in IPN are left alone.
- The "−/+" button at the end of the title collapses the panel to one line; slot highlights stay, and transfer results show above the hotbar.

Put down the item on your cursor before a transfer. Closing the container stops it.

Item tooltips show how many more are needed, and shulker box tooltips list the needed items inside (**Schematic needs in item tooltips**, works next to ShulkerBoxTooltip).

While schematics are hidden or the selected placement is disabled, the schematic stays quiet: no info panel, no container helper, no tooltip lines (**Hidden schematics stay quiet**).

## Block replacements

On the **Replacements** tab any schematic block except blocks with contents (chests, signs) can be swapped for another in the selected placement:

1. Pick a schematic block on the left.
2. Pick what to build instead on the right; similar blocks come first, and **Search blocks** finds a block by name or id.
3. **Keep the original block** undoes a swap, **Reset all** removes every swap in the placement.

Ghosts, the verifier, materials, easy place and paste see the swap right away. The file is not changed; swaps are kept with the placement. **Use block replacements** in the settings turns them off without deleting them.

## Verifying a build

The mod compares every schematic section with the world whenever a block changes or a chunk loads, on background threads.

| Category | Default color | Meaning |
|---|---|---|
| Missing | Light blue `2FC7FF` | The schematic needs a block, the world has air |
| Extra | Pink `FF4FD8` | The schematic expects air, the world has a block |
| Wrong block | Red `FF4444` | A different kind of block |
| Wrong state | Orange `FFA62B` | The same block with a different rotation or property |

Colors are changed under **Settings → Rendering → Error highlight** (swatches, arrow keys, an `RRGGBB` field, **Reset colors to default**). Highlights are drawn only on open faces (not covered by a neighbouring block, water, ice or glass) and only in loaded chunks.

The **Verifier** tab lists up to 200 nearest errors. Category switches control what is shown. Clicking a row frames the spot in the world; **Clear highlight** removes the frame. A line at the bottom says how many blocks in unloaded chunks were not checked.

Verification rules (**Settings → Checking**):

- **Ignore volatile states** (on) — not errors: redstone power, plant age, leaf distance, a lit furnace, a pressed button, an extended piston. Facing, repeater delay, comparator mode, note, levers, open doors, lit candles and campfires are compared strictly.
- **Count similar blocks as correct** (off) — accepts a block of the same kind: stone for quartz, plain glass for stained, any stairs with the same facing. Redstone parts, rails, falling blocks, walls and blocks with contents stay strict.
- **Dirt and grass are interchangeable** (on).
- **Hidden blocks count as correct** (on) — on a server a wrong block covered on all six sides is not reported, because anti-xray sends fake blocks there. Does not apply in singleplayer.
- **Check unloaded chunks with Voxy** (on) — with Voxy and VoxyMap, unloaded parts are compared with the blocks Voxy has stored. A section's verdict is also remembered after its chunk unloads, so the percentage does not jump depending on where you fly.

**100% complete** is the share of correct non-air blocks. Check extra blocks in air, container contents and entities separately. For a final check: all layers, no bounds, every category on.

## New schematic: saving a build

1. Hold the stick, **Area** mode (`Ctrl` + scroll).
2. Left-click the first corner, right-click the second. Both corner cells are included.
3. **H → New schematic**: adjust corners by typing numbers; **Here** puts a corner at your position.
4. For an uneven shape press **New box** and select the next part. Each box becomes a sub-region of the schematic, named after the box.
5. Type a **File name** and press **Save**. If the file exists, the button turns into **Replace**.

Details:

- The file is saved as `.litematic` in the root of the schematics folder. The author comes from the **Author** setting, or your player name when it is empty.
- The limit is 268,435,456 cells in total (the `.litematic` format limit); air and overlapping boxes count. The mod checks memory first and, if it is short, suggests giving Minecraft more memory or splitting the area.
- The tab warns about unloaded chunks: they would be saved empty. With Voxy and VoxyMap such chunks are taken from Voxy — blocks only, without chest contents, sign text or entities. After saving, the mod says how many chunks were missing or taken from Voxy.
- In singleplayer the full block entity data and entities are saved. On a server, what the client knows; armor stands, item frames and other entities are included there too.
- On a server sections are copied in 5 ms portions, so a large area saves without freezing the game.
- With Axiom: **Take Axiom selection** adds a box from the Axiom selection, **Select in Axiom** hands the boxes to Axiom, **Save Axiom clipboard** saves what you copied in Axiom to `.litematic` with container contents and entities.

## Paste, fill, replace and delete

All four change real blocks and need **creative mode**. The mod has no undo of its own: stopping ends the operation but does not restore what was changed. Back up the world before a large operation.

Each action is requested with the first click and carried out with the second (the confirmation lasts about 4 seconds). A right-click with the tool cancels and stops.

### Paste

The **Paste** button on the Placements tab or in the editor, or the tool in **Paste** mode.

| "Paste" setting | What happens |
|---|---|
| replace everything | The world is brought to the schematic, including its air |
| keep world under air | Only non-air blocks are placed; schematic air leaves the world alone |
| fill air only | Blocks go into empty cells only |

- Only what is inside the current layer and X/Z bounds is pasted (the layer is fixed when the paste starts). To paste everything, use All layers and no bounds.
- Sub-regions, the grid and replacements are applied. Unloaded sections are skipped.
- **In singleplayer** paste is direct: chest and sign contents are carried over (**Paste container data**), and after the blocks the schematic's entities are placed with rotation and mirroring (**Paste entities**); pasting again does not duplicate them. The result shows after the last batch.
- **On a server** paste uses `/setblock` and `/fill ... strict`, 8 commands per tick by default (**Paste commands per tick**, 1–16). Without operator rights it slows to about one command per second so the server does not kick you for spam. Container contents and entities are not pasted through commands. "Commands sent" is not a confirmation from the server; check the result in the world.
- With Axiom, **Paste with Axiom** hands the placement to Axiom's paste exactly where it stands (with rotation, sub-regions, grid, replacements, layer and bounds). Move the gizmo and confirm in Axiom; undo through Axiom's history.

### Fill, replace and delete

These work on **every fully set selection box** (not on a placement), with a limit of 64 million blocks.

- **Fill**: aim at a block in the world and press `Alt` + middle click; it becomes the fill block (with its exact state).
- **Replace**: `Alt` + middle click picks what to replace with, `Alt` + `Shift` + middle click picks what to replace (any state). The chosen blocks show on the info panel.
- **Delete** fills the boxes with air.

On a server these use `/fill ... strict` and `/fill ... replace`.

## Zones for team builds

The **Zones** tab splits the selected placement into parts so a team can build together without getting in each other's way.

1. On the right set **Along X**, **Along Z** and **Floors** (up to 16 parts per axis). The size comes from the box around the whole placement, grid included.
2. Select a zone and press **Take it**. **Only this zone** shows just that zone (it is an ordinary layer plus X/Z bounds), so ghosts, easy place and materials cover only your zone. **Show everything** returns the full view, **Give it up** frees the zone.
3. Every zone shows its progress against the real blocks in the world, so your teammates' work shows up without any messages. The info panel shows "Your zone N: percent".

**Talking to the team.** Under **Team**, enter your teammates' names separated by commas and choose a channel:

| Channel | Where it goes |
|---|---|
| Private messages (`/msg`) | To each teammate in the list separately |
| Team chat (`/teammsg`) | Once, to your whole vanilla team (`/team`) |
| Custom command | A template such as `clan chat {text}` or `w {player} {text}`; with `{player}`, sent to each teammate |

The mod **never writes to the public chat**. Commands that write to everyone (`say`, `me`, `tellraw`, `broadcast` and the like) are refused; if the chosen command does not exist on the server, the mod sends nothing and says so. Commands go out at most once every 1.25 s. For teammates with the mod the `[LP-zones ...]` service lines are hidden (**Hide zone service messages**), and messages from players not in the list are ignored.

**Send the plan** sends the split: it applies for a teammate who has the same schematic in the same spot (for example, shared through Syncmatica). **Who took what?** asks teammates to repeat their zones — handy after joining. If two players take the same zone at the same moment, the last message wins.

## Shared server schematics (Syncmatica)

If the server runs the Syncmatica plugin (Paper, Purpur, Folia) or the Syncmatica mod (Fabric server), the mod connects to it on its own when you join, and chat says how many shared schematics the server has.

On the **Server** tab:

- **Load** (or double-click) downloads a shared schematic and shows it; **Unload** hides it for you only, it stays on the server.
- **Share "..."** uploads the selected placement to the server.
- **Remove from server** deletes it for everyone, with a second press.
- To move a shared schematic for everyone, unlock it on the Placements tab (the server lets one player edit at a time), move it and lock it again; the new position goes to everyone.

Downloaded files live in `schematics/syncmatics/` and are matched by hash. If the separate Syncmatica mod is installed, the tab is off and that mod handles shared schematics. To turn the link off: **Settings → Tools → Server shared schematics (Syncmatica)**.

## Add-ons and compatibility

The **Add-ons** tab shows every integration as Working, Waiting, Off, Not installed, Error or Conflict, together with its settings and problem log. **Copy report** copies the mod and problem details for a bug report.

| Mod | What it adds |
|---|---|
| Sodium | When Voxy depth is not available, the schematic is not drawn over sections that are not built yet |
| Iris | The schematic is drawn after the final shader pass; the view from above pushes the shader pack's fog back |
| Voxy | With shaders the mod adds Voxy's distant terrain depth, so the schematic does not show through far mountains |
| VoxyMap | Placements on the 3D map (an outlined box with name and progress), in the objects panel and on the minimap; checking and saving unloaded chunks from Voxy data. The "Schematics (Litematica Plus)" layer in VoxyMap's settings turns the map display off |
| Jade | The tooltip shows the schematic block you aim at: placed or not, matching or not, the expected state |
| Stack Calculator | A right-click on a material panel row sends the amount to the calculator |
| Inventory Profiles Next | Locked slots are left alone by the material helper, easy place and auto tool switch |
| Mod Menu | A settings button in the mod list |
| Axiom | `.bp` blueprints, paste through Axiom, selections both ways, saving the Axiom clipboard. While the Axiom editor is open the mod does not catch clicks and keys (**Step aside in the Axiom editor**) |
| Syncmatica (server) | Shared schematics, see above |
| Litematica | Conflict: keep only one of the two mods |

Checked by the automated in-game test: without render mods, and with Sodium 0.9.3-alpha.1 + Iris 1.11.6 + Complementary Reimagined r5.9.3; the material panel with Inventory Profiles Next 2.3.8, Stack Calculator 1.2.0 and Jade 26.3.3. The mod has not been tested automatically on a real server with an anti-cheat.

## Settings reference

**H → Settings.** Categories are on the left, with sections inside. Above the categories is **Search settings** (`Ctrl+F` jumps to it, `Esc` clears): it searches names, tooltips and keys. Changes are applied with **Save** and saved when you leave the tab; **Revert** undoes unsaved changes (key bindings are not undone).

In the tables: name in the menu / key in `config/litematicaplus.json` / default.

### Rendering

| Setting / key | Default | What it does |
|---|---|---|
| **Schematic** | | |
| Show schematics / `renderEnabled` | On | Main switch for ghosts, highlights and frames. When off, the info panel, pick block and easy place ignore schematics |
| Ghost blocks / `renderGhostBlocks` | On | See-through blocks where the schematic is not built |
| Ghosts only on errors / `ghostErrorsOnly` | Off | Ghosts only where a wrong block stands; missing blocks are not drawn |
| Ghosts only in loaded chunks / `ghostsOnlyLoaded` | On | When off, the schematic is drawn beyond loaded chunks and can show through Voxy's distant terrain |
| Chests and special blocks / `renderBlockEntities` | On | Ghosts of chests, signs, banners, heads |
| Ghosts over wrong blocks / `renderCollidingBlocks` | Off | Draws the expected block over a wrong one |
| Ghost opacity / `ghostAlpha` | 65% | 20–100% |
| Ghost tint / `ghostColor` | `#FFFFFF` | White keeps the original textures |
| Render distance / `renderDistance` | 320 | 32–2048 blocks from the camera; does not load more of the world |
| **Error highlight** | | |
| Error overlay / `renderOverlay` | On | Colored error outlines |
| X-ray overlay / `overlayThroughWalls` | Off | Visible through blocks |
| Overlay fill / `overlayFillAlpha` | 22% | 0–100% |
| Overlay outline / `overlayLineAlpha` | 90% | 0–100% |
| Missing / `missingColor`, Wrong block / `wrongBlockColor`, Wrong state / `wrongStateColor`, Extra / `extraColor` | see [Verifying](#verifying-a-build) | `RRGGBB` color; **Reset colors to default** |
| Highlight what you can place / `inventoryHighlight` | Off | Outlines the nearest blocks whose item you carry (shulker boxes not counted) |
| **Frames** | | |
| Placement outlines / `renderPlacementBoxes` | On | Frames of schematics and sub-regions |
| Selection outlines / `renderSelectionBoxes` | On | Frames of new-schematic boxes |

### Checking

| Setting / key | Default | What it does |
|---|---|---|
| **What counts as correct** | | |
| Ignore volatile states / `lenientStates` | On | Properties that change by themselves are not errors |
| Count similar blocks as correct / `looseBlocks` | Off | A block of the same kind counts as correct |
| Dirt and grass are interchangeable / `soilInterchangeable` | On | Dirt for grass and back is correct |
| Hidden blocks count as correct / `trustHiddenBlocks` | On | Against anti-xray, servers only |
| Use block replacements / `materialSwap` | On | Swaps from the Replacements tab |
| **What is counted** | | |
| Count only the visible part / `countVisibleOnly` | On | Progress, counters and materials follow the layer and bounds |
| Check unloaded chunks with Voxy / `useWorldCache` | On | Needs Voxy and VoxyMap |

### HUD

| Setting / key | Default | What it does |
|---|---|---|
| Info HUD / `hudEnabled` | On | Progress, errors, materials, layer, tool mode |
| Info panel position... / `hudX`, `hudY` | Top left | Drag with the mouse, arrow keys (10 px with `Shift`), corner buttons |
| HUD material rows / `hudMaterialLines` | 6 | 0–16; 0 hides the list |
| Note tuning on the HUD / `noteTuningHud` | On | How many note blocks are left to tune |
| Render diagnostics on the HUD / `hudDiagnostics` | Off | Sections, queue, Voxy depth, block updates per minute |

### Tools

| Setting / key | Default | What it does |
|---|---|---|
| **Tool** | | |
| Tool item active / `toolEnabled` | On | The tool item works |
| Tool item / `toolItem` | `minecraft:stick` | Any item ID |
| Pick block from ghost / `pickBlockEnabled` | On | Middle-click a ghost to take the block (granted in creative) |
| **Paste** | | |
| Paste / `pasteReplace` | replace everything (`ALL`) | `ALL`, `NON_AIR_ONLY`, `INTO_AIR_ONLY` |
| Paste commands per tick / `pasteCommandsPerTick` | 8 | 1–16, servers only |
| Paste container data / `pasteBlockEntityData` | On | Chests, signs — direct paste |
| Paste entities / `pasteEntities` | On | Direct paste in singleplayer only |
| **Schematic library** | | |
| 3D schematic previews / `schematicPreview` | On | Picture on the Schematics and Placements tabs |
| Detailed preview models / `previewModels` | On | Off: colored cubes, faster |
| Author / `defaultAuthor` | empty | Up to 64 characters; empty uses your player name |
| **Team play** | | |
| Server shared schematics (Syncmatica) / `syncmaticaEnabled` | On | Answer Syncmatica servers |
| Hide zone service messages / `zoneHideMessages` | On | `[LP-zones]` lines are not shown in chat |
| **Axiom** (when installed) | | |
| Step aside in the Axiom editor / `axiomEditorPause` | On | The mod does not catch input in the Axiom editor |

### Easy place

| Setting / key | Default | What it does |
|---|---|---|
| **Basics** | | |
| Easy place / `easyPlaceEnabled` | Off | Main switch |
| Hold to keep placing / `easyPlaceHold` | On | Hold the button and sweep |
| Pace / `easyPlacePreset` | Balanced (`BALANCED`) | `BALANCED` 2, `SAFE` 4, `FAST` 1, `CUSTOM` |
| Custom delay, ticks / `easyPlaceDelayTicks` | 2 | 0–20, for the Custom pace |
| Random extra tick / `easyPlaceJitter` | Off | +0 or +1 tick |
| No stray blocks / `easyPlaceRestrictArea` | On | No manual placing inside and within 2 blocks |
| Placement guard / `placementRestriction` | Off | Blocks unwanted manual placing even without easy place |
| Open containers as usual / `easyPlaceAllowInteraction` | On | A click opens the chest, `Shift` + click places the ghost |
| Explain refusals / `easyPlaceDiagnostics` | On | Reason above the hotbar |
| **Rotation** | | |
| Turn for the server / `easyPlaceFakeRotation` | On | Needed look direction without moving the camera |
| Turn lead, ticks / `easyPlaceRotationLeadTicks` | 1 | 0–3 |
| Restore look after click / `easyPlaceRotationRevert` | On | |
| Only exact orientation / `easyPlaceStrictOrientation` | On | Skip a block with no exact option |
| Hidden-face and air clicks / `easyPlaceAirPlacement` | On | For blocks without support |
| **Finishing blocks** | | |
| Finish block states / `easyPlaceFinishStates` | On | Extra clicks after placing |
| Light and put out candles and campfires / `easyPlaceIgnite` | On | |
| Waterlog blocks with a bucket / `easyPlaceWaterlog` | Off | |
| Observers wait / `easyPlaceObserverDetect` | Off | |
| Place schematic entities / `entityPlacement` | On | Frames, paintings, armor stands |
| **Items** | | |
| Use items with data / `easyPlaceIgnoreItemData` | Off | Renamed items and items with contents |
| Substitute materials / `easyPlaceSubstitutes` | Off | Plus **Add held block** and **Clear list** |
| Locked slots / `lockedHotbarSlots` | none | Bit mask of slots 1–9 |

### Breaking

| Setting / key | Default | What it does |
|---|---|---|
| **Protection** | | |
| Excavation guard / `excavationGuard` | Off | Works together with easy place |
| Guard: outside the schematic / `excavationGuardOutside` | On | |
| Guard: correct blocks / `excavationGuardCorrect` | On | |
| Guard: say why / `excavationGuardHint` | On | |
| Do not spoil schematic blocks with tools / `toolUseGuard` | On | |
| **Help** | | |
| Auto tool switch / `autoToolSwitch` | Off | |
| Break wrong blocks / `breakWrongBlocks` | Off | |
| Swap ground variants / `terrainAutoReplace` | Off | |

### Materials

| Setting / key | Default | What it does |
|---|---|---|
| **In containers** | | |
| Material helper in chests / `materialHelper` | On | Slot highlights |
| Material panel next to containers / `materialPanel` | On | The side panel |
| Take exactly what is needed / `materialExactTake` | On | Split stacks; off: whole stacks only |
| Schematic needs in item tooltips / `materialTooltips` | On | |
| Hidden schematics stay quiet / `materialHelperFollowsRender` | On | |
| **Stock** | | |
| Count the ender chest in materials / `enderChestMaterials` | On | |
| Remember opened containers / `containerMemory` | On | For the current session |
| Highlight containers with missing materials / `containerHighlight` | On | Within 64 blocks |

### Hotkeys

Every mod key, in the sections Menus, Actions, Switches and Other. See [Mod hotkeys](#mod-hotkeys).

### Speed

| Setting / key | Default | What it does |
|---|---|---|
| Worker threads / `workerThreads` | Auto (0) | 0–16; auto is half the CPU cores, at most 6 |
| Section uploads per frame / `meshUploadsPerFrame` | 24 | 1–256; more shows schematics faster but can stutter |
| World scan budget, µs / `snapshotBudgetMicros` | 600 | 200–10000; more checks faster but lowers FPS |

### Printer (printer edition only)

| Setting | Default | What it does |
|---|---|---|
| Printer | Off | Places missing blocks by itself |
| Printer reach | 4 | 1–6 blocks |

### File-only options

| Key | Default | Purpose |
|---|---|---|
| `schematicsDirectory` | `"schematics"` | Schematics folder relative to the profile, or an absolute path such as `E:/MinecraftSchematics`. No `..`, at most 260 characters |
| `showMissing`, `showExtra`, `showWrongBlock`, `showWrongState` | `true` | Category switches on the Verifier tab |
| `materialPanelCollapsed` | `false` | Whether the material panel is collapsed |
| `zoneTeam`, `zoneTransport`, `zoneCommand` | empty, `MESSAGE`, empty | Zone channel settings (changed on the Zones tab) |
| `configVersion` | — | Internal, leave it alone |

## Ready-made setups

| Goal | What to turn on |
|---|---|
| Hologram only, building by hand | Easy place off; ghosts, highlights and pick block on |
| Normal survival building | Easy place on, Balanced pace, exact orientation and finishing states on |
| The server rejects clicks | Safe pace, air clicks off, turn lead 2, explain refusals on |
| Building floor by floor | `F7` → Y axis, Single layer; Count only the visible part on; bind Move layer up/down |
| A big build with friends on a server | Zones tab: split, hand out, `/msg` or `/teammsg` channel |
| Final check | All layers, bounds reset, every category on; for strictness turn off Ignore volatile states and Hidden blocks count as correct |
| A big schematic stutters | Lower the render distance, turn off special blocks and detailed previews, threads on auto; then lower section uploads and the scan budget |
| Paste must not ruin the terrain | Paste: "keep world under air" or "fill air only" |
| Ghosts of one block get in the way (water, for example) | Materials → select it → **Hide ghosts** |

## Files and recovery

All paths are inside the folder of the **current game profile**.

| Path | What it holds |
|---|---|
| `config/litematicaplus.json` (+ `.bak`) | Main settings |
| `config/litematica-plus-session.json` (+ `.bak`) | Placements, selections, layers, bounds, replacements, hidden ghosts, tool mode — per server or world and dimension |
| `config/litematica-plus-substitutes.json` (+ `.bak`) | Substitute material list |
| `config/litematicaplus-printer.json` | Printer settings |
| `config/litematicaplus/previews/` | Preview cache (safe to delete) |
| `schematics/` | Schematics |
| `schematics/syncmatics/` | Downloaded shared server schematics |
| `litematica-plus/materials/` | Material exports |
| `options.txt` | The mod's main keys |

- Files are written atomically: a temporary file, a swap and a `.bak` backup. If the main file is damaged, the mod reads the `.bak`.
- A session file from a newer mod version is opened read-only.
- After joining again, tool modes that change the world (Paste, Fill, Replace, Delete) fall back to Area.
- Schematics are not stored inside the session file: if a schematic file is moved or deleted, its placement will not load.
- Overwriting a `.litematic` when saving keeps no copy of the old schematic; back it up yourself.

Editing the config by hand: close the game, copy the file and its `.bak`, edit the JSON (forward slashes are easiest for Windows paths), start the game. For a full settings reset rename **both** `litematicaplus.json` **and** `litematicaplus.json.bak`, otherwise the mod restores the backup.

## Troubleshooting

| Problem | What to check |
|---|---|
| `H` does not open the menu | The mod is in this profile's folder; the key in Controls; key conflicts. Open it through Mod Menu. Old profiles may keep the former `K` key — rebind it |
| Litematica warning | Keep only one of the mods in `mods` |
| A file is missing from the list | The right profile folder, a supported extension, the current subfolder, an empty filter, **Refresh** |
| A schematic fails to load | The message on the Placements tab and `logs/latest.log`; the file may be damaged |
| Loaded but not visible | Show schematics, Shown on the placement, coordinates and dimension, layer and bounds, render distance, hidden ghosts |
| The far part is not visible | Chunks are not loaded: Ghosts only in loaded chunks is on |
| Ghosts show through distant mountains | Turn on Ghosts only in loaded chunks; check Add-ons → Voxy |
| Right-click sets a selection corner | The tool is in your hand: switch item or turn the tool off |
| Right-click builds nothing | Easy place and Show schematics on; menu closed; close enough; layer; item in inventory; support. Turn on Explain refusals |
| "Not in your inventory" although you have it | It is in a shulker box, in the off hand, or has data (a name, contents) |
| A chest opens instead of placing | That is Open containers as usual: use `Shift` + click |
| Cannot place a block by hand near the schematic | No stray blocks (2 blocks around) or Placement guard |
| Cannot break blocks | Excavation guard and its rules |
| A block faces the wrong way | Turn for the server, turn lead 2–3, Only exact orientation, support |
| No panel at a chest | A placement is selected, helper and panel on, schematic not hidden; reopen the container |
| A transfer stopped | An item on the cursor, a full inventory or container |
| Materials and percentage change with the layer | Count only the visible part is on |
| 100% but errors remain | The percentage covers non-air blocks; extra blocks, entities and contents are separate |
| Only part was pasted | A layer or X/Z bounds are set |
| Paste does nothing | Creative mode; on a server, permission for `/setblock` and `/fill`; chunks loaded; no other operation running |
| A saved schematic has holes | Some chunks were not loaded; fly over them or install Voxy + VoxyMap |
| The Server tab is off | The separate Syncmatica mod is installed, or the server has no Syncmatica |
| The schematic vanished after a render error | `Schematic rendering failed` in the log; restart the game, check the mod and shader combination |
| Placements are gone after joining | Same server address or world folder and dimension; the schematic file is in place; the session is not read-only |

For integration problems open **Add-ons**, press **Copy report** and attach it to your bug report.

## Limitations

- On a server, entities and container contents are not pasted (command length limit). In survival, frames, paintings and armor stands are placed with easy place.
- Litematica schematic projects are not supported.
- With Iris shaders, ghosts are drawn on top of the finished frame: the pack's shadows, bloom and tone mapping do not apply to them, and chests and other special blocks are drawn opaque.
- In the view from above, terrain is visible only in loaded chunks; some shader packs may still hide it in haze.
- Materials for entities are not part of the material list.
- Easy place writes only the plain front text of signs and does not fill chests.
