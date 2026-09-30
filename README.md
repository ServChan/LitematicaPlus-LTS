# Litematica Plus LTS

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.1.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Schematic holograms, area selection, verification, material lists, easy place and paste. A from-scratch, standalone replacement for Litematica that reads and writes `.litematic` files.

## Русский

### Что это

`Litematica Plus LTS` (mod id `litematicaplus`) — клиентский Fabric-мод для Minecraft 26.3, написанный с нуля вместо Litematica. Ему не нужны ни Litematica, ни MaLiLib: он сам читает и сохраняет `.litematic`, показывает схему полупрозрачной голограммой, сравнивает её с миром и помогает строить. Папка `schematics/` общая с Litematica, поэтому существующая библиотека схем открывается без переноса.

### Возможности

- **Файлы**: чтение и запись `.litematic` (несколько регионов, отрицательные размеры, блок-сущности, сущности), импорт ванильных структур `.nbt` и Sponge `.schem` v2/v3; состояния блоков из старых версий проходят через ванильный DataFixer, неизвестные блоки становятся воздухом, а не роняют загрузку, и мод сообщает в чате, сколько их было;
- **Размещения**: сколько угодно схем в мире, перемещение, поворот на 90°, отражение по X/Z, блокировка, отключение отдельных подрегионов; список хранится отдельно для каждого сервера и измерения;
- **Голограмма**: блоки-призраки с настраиваемой прозрачностью, сундуки и другие блоки со специальной моделью, жидкости; показываются только там, где блока ещё нет. По умолчанию призраки рисуются только в прогруженных чанках («Призраки только в прогруженных чанках» в настройках отрисовки), как в Litematica, чтобы с Voxy и шейдерами они не просвечивали сквозь дальний рельеф;
- **Проверка в реальном времени**: каждая секция схемы сравнивается с миром при любом изменении блока или загрузке чанка; цветная подсветка «нет блока», «не тот блок», «не то состояние», «лишний» с подписями в интерфейсе; по желанию — сквозь стены. Подсветка рисуется только на открытых гранях и только в прогруженных чанках; результат сверки секции запоминается после выгрузки чанка, а с Voxy и VoxyMap непрогруженные части сверяются с блоками, которые сохранил Voxy («Сверять непрогруженное по Voxy»), поэтому процент и счётчики не меняются от того, куда вы летите. На сервере блок, закрытый со всех шести сторон, не считается ошибкой «не тот блок» («Скрытые блоки считать верными»): античит-подмена анти-ксрея иначе красит всё подземелье;
- **Умное сравнение**: свойства, которые меняются сами (листва `distance`/`persistent`, сила редстоуна, горящая печь, нажатая кнопка, выдвинутый поршень, возраст посадок), по умолчанию не считаются ошибкой; положение рычага, зажжённые свечи и костры, открытые двери сравниваются строго;
- **Слои**: все, один слой, этот и ниже/выше, диапазон — по любой оси;
- **Материалы**: всего / осталось / есть в инвентаре (включая шалкеры), сортировка, экспорт в Excel (`.xlsx` с листами «Сводка», «Собрать», «Все материалы»), CSV, Markdown, JSON или в буфер обмена; файлы сохраняются в `litematica-plus/materials/`, ссылка на файл и папку появляется в чате; двойные плиты, свечи, слои снега, огурцы и вёдра жидкостей считаются правильно, цветок в горшке — это горшок и цветок, торт со свечой — торт и свеча, верхние половины дверей и кроватей не удваиваются;
- **Помощник материалов**: в сундуках, бочках, шалкерах, раздатчиках и воронках нужные выбранному размещению предметы подсвечены зелёным, шалкеры с ними — жёлтым; кнопка «Взять материалы» или горячая клавиша переносит их в инвентарь по одному стаку за тик;
- **Лёгкая установка** со встроенными возможностями EasyPlaceFix: правая кнопка по призраку ставит нужный блок, удержание кнопки ведёт установку вдоль прицела. Перед кликом мод перебирает направления взгляда и точки клика, моделирует установку и выбирает вариант, дающий состояние из схемы; нужный поворот отправляется серверу, а камера остаётся на месте. Темп считается в игровых тиках (пресеты «Баланс» 2, «Осторожно» 4, «Быстро» 1, «Свой»), есть случайный тик. После установки мод доводит состояние: задержка повторителя, режим компаратора, рычаг, открытые люки, двери и калитки, двойные плиты, свечи, цветок в горшке, высота ноты (очередь кликов с панелью прогресса); текст табличек берётся из схемы, у крафтера отключаются нужные слоты. Дополнительно: наблюдатель ждёт блок перед собой, список замен материалов, защита при раскопках, автосмена инструмента, замена вариантов земли и выкапывание неверных блоков;
- **Сущности схемы**: рамки и светящиеся рамки с предметом и поворотом, картины и стойки для брони с бронёй отмечены рамкой-маркером; наведитесь и нажмите использование;
- **Без лишних блоков**: пока включена лёгкая установка, блок из руки нельзя поставить вручную внутри размещения и в двух блоках от него, а после установки по призраку удержание кнопки больше не ставит второй блок рядом; отдельная «Защита от ошибок» работает и без лёгкой установки;
- **Карта VoxyMap**: с установленным VoxyMap размещения видны на 3D-карте рамкой с названием и процентом, в панели объектов и на миникарте; призраки на карте рисуются вокруг центра обзора карты; слой «Схемы (Litematica Plus)» в настройках VoxyMap выключает всё это;
- **Редактор размещения**: прозрачный экран с панелью справа, схема видна в мире и двигается сразу. Координаты X/Y/Z вводятся с клавиатуры, `Ctrl+V` вставляет три числа; перетаскивание мышью, стрелки, шаг 1/5/16/64, поворот, отражение, вид сверху с панорамой и масштабом;
- **Горячие клавиши**: вкладка «Клавиши» в настройках мода — назначение клавиши или кнопки мыши, подсветка конфликтов, `Esc` снимает назначение;
- **Пипетка**: средняя кнопка по призраку берёт нужный предмет (в выживании — из инвентаря, в творческом — выдаёт);
- **Выделение и сохранение**: области из нескольких боксов, сохранение в `.litematic`; в одиночной игре сохраняются полные данные блок-сущностей и сущности;
- **Вставка и удаление** (творческий режим): в одиночной игре — напрямую, с содержимым сундуков; на сервере — командами `/setblock` и `/fill ... strict` с ограничением скорости (по умолчанию 8 команд за тик, без прав оператора — одна команда примерно в секунду, чтобы сервер не кикнул за спам). Оба действия требуют подтверждения вторым кликом, удаление ограничено 64 млн блоков;
- **Производительность**: схема на 4,4 млн блоков загружается из файла за ~0,1 с, разворачивается в мир за ~0,2 с и полностью проверяется и мешится за ~1,7 с в фоновых потоках; FPS при этом держался на лимите 120 (замер на RTX 5080 автотестом из раздела «Сборка»). Главный поток только копирует секции мира в пределах бюджета времени; сравнение и сборка мешей идут в фоне, готовые меши лежат в постоянных GPU-буферах и рисуются одним проходом.

Что пока не перенесено из Litematica:

- вставка сущностей командами (в выживании рамки, картины и стойки ставятся лёгкой установкой), прочие сущности, старый формат `.schematic` (MCEdit), сдвиг отдельных подрегионов, проекты схем, режимы инструмента «заливка/замена», сетка размещений;
- на сервере вставка не переносит содержимое контейнеров (ограничение длины команды);
- с шейдерами Iris призраки рисуются поверх готового кадра, поэтому на них не действуют тени, блум и тональная коррекция пакета; сундуки и другие блоки со специальной моделью рисуются игрой как обычные, непрозрачными.

- в виде сверху рельеф виден только в прогруженных вокруг игрока чанках; с шейдерами туман пакета отодвигается через значение `far` от Iris, и в отдельных пакетах рельеф всё равно может тонуть в дымке;
- материалы для сущностей в список не входят.

Совместимость проверена автотестом в игре: без модов рендера и с Sodium `0.9.3-alpha.1` + Iris `1.11.6` с пакетом Complementary Reimagined r5.9.3. Тест идёт на встроенном сервере одиночной игры: на настоящем многопользовательском сервере с античитом мод не проверялся, поэтому темп «Осторожно» и отключение кликов по воздуху оставлены в настройках.

### Управление

Клавиши назначаются в меню мода (Настройки → Клавиши) или в Options → Controls → Litematica Plus.

| Действие | Клавиша по умолчанию |
|---|---|
| Открыть меню | `K` |
| Редактор размещения в мире | не назначена |
| Показать/скрыть схемы, лёгкая установка, инструмент | не назначены |
| Следующий режим инструмента, режим слоёв, слой выше/ниже | не назначены |
| Установка удержанием, защита при раскопках, автосмена инструмента, ломать неверные блоки, замена материалов | не назначены |
| Взять материалы из открытого контейнера | не назначена |

В редакторе размещения:

| Действие | Ввод |
|---|---|
| Двигать схему по земле | перетаскивание левой кнопкой |
| Ввести координату | клик по полю X/Y/Z, число, `Enter` |
| Вставить «x y z» из буфера | `Ctrl+V` |
| Сдвиг на шаг (с `Shift` — на 10 шагов) | стрелки, `PgUp`/`PgDn`, кнопки панели |
| Высота / масштаб в виде сверху | колесо |
| Поворот / отражение / вид сверху | `R` / `M` / `T` |
| Двигать вид сверху | перетаскивание правой кнопкой |

С инструментом в руке (по умолчанию палка):

| Действие | Ввод |
|---|---|
| Сменить режим (Область → Перемещение → Вставка → Удаление) | `Ctrl` + колесо или средняя кнопка |
| Область: угол 1 / угол 2 | левая / правая кнопка |
| Перемещение: перенести размещение / повернуть на 90° | левая / правая кнопка |
| Вставка / Удаление: начать (два клика: запрос и подтверждение) / отменить | левая / правая кнопка |
| Сдвинуть бокс или размещение на 1 блок по взгляду | `Alt` + колесо |
| Взять свою позицию вместо блока под прицелом | `Alt` + клик |

Без инструмента: правая кнопка по призраку или маркеру сущности — лёгкая установка (если включена), средняя кнопка — пипетка.

### Настройки

Меню (`K`) или **Mod Menu**: вкладки «Быстрый доступ», «Схемы», «Размещения», «Область», «Материалы», «Проверка», «Настройки». Настройки лежат в `config/litematicaplus.json`, размещения и выделения — в `config/litematica-plus-session.json`, список замен материалов — в `config/litematica-plus-substitutes.json` (файлы пишутся атомарно, с резервной копией `.bak`). У параметров лёгкой установки есть подсказки при наведении.

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API** `0.161.0+26.3`.
2. Отключите Litematica, MaLiLib и EasyPlaceFix, если они стоят: их функции уже внутри мода.
3. Скопируйте `litematica-plus-lts-1.1.0.jar` из `build/libs/` в папку `mods/`.
4. **Mod Menu** — опционально, открывает меню мода из списка модов.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/litematica-plus-lts-1.1.0.jar`.

Проверка на своих схемах (только чтение): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<папка schematics>"`. Автоматический тест в игре (создаёт мир, размещает схему, проверяет подсветку, вставку, сохранение, лёгкую установку 33 видов блоков, сущности, помощник материалов, экспорт, редактор размещения, назначение клавиш и делает скриншоты): `.\gradlew.bat runClientGameTest`.

---

## English

### What It Is

`Litematica Plus LTS` (mod id `litematicaplus`) is a client-side Fabric mod for Minecraft 26.3, written from scratch as a replacement for Litematica. It needs neither Litematica nor MaLiLib: it reads and writes `.litematic` itself, shows a schematic as a translucent hologram, compares it with the world, and helps you build. It shares the `schematics/` folder with Litematica, so an existing library opens without any migration.

### Features

- **Files**: reads and writes `.litematic` (multiple regions, negative sizes, block entities, entities), imports vanilla structure `.nbt` and Sponge `.schem` v2/v3; block states from older versions go through the vanilla DataFixer, and unknown blocks become air instead of failing the load, with a chat message saying how many there were;
- **Placements**: any number of schematics per world, with move, 90° rotation, X/Z mirror, lock, and per-sub-region toggles; the list is stored per server and dimension;
- **Hologram**: ghost blocks with adjustable opacity, chests and other blocks with a special model, and fluids; drawn only where the block is not there yet. By default ghosts are drawn only in loaded chunks ("Ghosts only in loaded chunks" under the rendering settings), as Litematica does, so with Voxy and shaders they do not show through distant terrain;
- **Live verification**: every schematic section is compared with the world whenever a block changes or a chunk loads; colored highlights for "missing", "wrong block", "wrong state" and "extra", always labeled in the UI; optionally visible through walls. Highlights are drawn only on exposed faces and only in loaded chunks; a section's verdict is remembered after its chunk unloads, and with Voxy and VoxyMap the unloaded parts are compared against the blocks Voxy has stored ("Check unloaded chunks with Voxy"), so the percentage and counters do not change depending on where you fly. On a server a block enclosed on all six sides is not counted as a "wrong block" error ("Hidden blocks count as correct"): an anti-xray's decoy blocks would otherwise paint the whole underground red;
- **Smart comparison**: properties that change by themselves (leaf `distance`/`persistent`, redstone power, a lit furnace, a pressed button, an extended piston, crop age) are not counted as errors by default; lever position, lit candles and campfires, and open doors are compared strictly;
- **Layers**: all, single layer, this and below/above, range — on any axis;
- **Materials**: total / still needed / in inventory (shulker boxes included), sorting, export to Excel (`.xlsx` with Summary, To gather and All materials sheets), CSV, Markdown, JSON or the clipboard; files go to `litematica-plus/materials/` and a link to the file and the folder appears in chat; double slabs, candles, snow layers, sea pickles and fluid buckets are counted correctly, a potted flower counts as a pot plus the flower, a candle cake as a cake plus a candle, and upper door halves and bed heads are not double-counted;
- **Material helper**: in chests, barrels, shulker boxes, dispensers and hoppers the items the selected placement still needs are highlighted green and shulker boxes holding them yellow; the Take materials button or hotkey moves them into your inventory one stack per tick;
- **Easy place** with the EasyPlaceFix feature set built in: right-click a ghost to place the right block, hold the button to keep placing along your aim. Before clicking, the mod tries look directions and click points, simulates the placement and picks the one that yields the schematic state; the needed turn is sent to the server while your camera stays still. Pacing is counted in game ticks (presets Balanced 2, Safe 4, Fast 1, Custom) with an optional random tick. After placing it finishes the state: repeater delay, comparator mode, lever, open trapdoors, doors and gates, double slabs, candles, potted flowers, note pitch (a click queue with a progress line on the HUD); sign text comes from the schematic and crafter slots are disabled as saved. Also: observers wait for the block they watch, a substitute material list, excavation guard, auto tool switch, ground variant swap and mining out wrong blocks;
- **Schematic entities**: item frames and glow item frames with their item and rotation, paintings and armor stands with armor get an outline marker; aim and use;
- **No stray blocks**: while easy place is on, a block in hand cannot be placed by hand inside a placement or within two blocks of it, and after placing on a ghost the held button no longer puts a second block next to it; the separate Placement guard also works without easy place;
- **VoxyMap**: with VoxyMap installed, placements appear on the 3D map as an outlined box with name and progress, in the objects panel and on the minimap; on the map the ghosts are drawn around the map's view centre; the "Schematics (Litematica Plus)" layer in VoxyMap's settings turns all of it off;
- **Placement editor**: a see-through screen with a side panel, so the schematic stays visible in the world and moves at once. X/Y/Z are typed on the keyboard and `Ctrl+V` pastes three numbers; mouse drag, arrow keys, step 1/5/16/64, rotate, mirror, and a view from above with pan and zoom;
- **Hotkeys**: a Hotkeys tab in the mod's settings binds a key or mouse button, marks conflicts, and `Esc` clears a binding;
- **Pick block**: middle-click a ghost to take the needed item (from the inventory in survival, granted in creative);
- **Selection and saving**: areas made of several boxes, saved as `.litematic`; in singleplayer the full block entity data and entities are saved;
- **Paste and delete** (creative): direct in singleplayer, including chest contents; on a server through rate-limited `/setblock` and `/fill ... strict` commands (8 commands per tick by default, and about one command per second without operator rights so the server does not kick for spam). Both actions need a confirming second click, and delete is limited to 64 million blocks;
- **Performance**: a 4.4 million block schematic loads from disk in ~0.1 s, is laid out in the world in ~0.2 s, and is fully verified and meshed in ~1.7 s on background threads while the frame rate stayed at its 120 FPS cap (measured on an RTX 5080 by the automated test described under Building). The main thread only copies world sections within a time budget; comparison and meshing run in the background, and finished meshes live in persistent GPU buffers drawn in a single pass.

Not ported from Litematica yet:

- pasting entities through commands (in survival, frames, paintings and armor stands are placed by easy place), other entity types, the legacy `.schematic` (MCEdit) format, moving individual sub-regions, schematic projects, the fill/replace tool modes, grid placements;
- server-side paste does not carry container contents (command length limit);
- with Iris shaders the ghosts are drawn on top of the finished frame, so a pack's shadows, bloom and tone mapping do not apply to them; chests and other blocks with a special model are drawn by the game as ordinary, opaque blocks.

- in the view from above, terrain is visible only in the chunks loaded around the player; with shaders the pack's fog is pushed back through the `far` value Iris provides, and some packs may still hide the terrain in haze;
- materials for entities are not part of the material list.

Compatibility was checked by the automated in-game test: without render mods, and with Sodium `0.9.3-alpha.1` + Iris `1.11.6` running the Complementary Reimagined r5.9.3 pack. The test runs on the integrated singleplayer server: the mod has not been tried on a real multiplayer server with an anti-cheat, which is why the Safe pace and the switch for air clicks stay in the settings.

### Controls

Keys are bound in the mod's menu (Settings → Hotkeys) or in Options → Controls → Litematica Plus.

| Action | Default key |
|---|---|
| Open the menu | `K` |
| Placement editor in the world | unbound |
| Show or hide schematics, easy place, tool item | unbound |
| Next tool mode, layer mode, layer up/down | unbound |
| Hold to keep placing, excavation guard, auto tool switch, break wrong blocks, substitute materials | unbound |
| Take materials from the open container | unbound |

In the placement editor:

| Action | Input |
|---|---|
| Move the schematic along the ground | drag with the left button |
| Type a coordinate | click the X/Y/Z field, type the number, `Enter` |
| Paste "x y z" from the clipboard | `Ctrl+V` |
| Nudge by the step (`Shift`: 10 steps) | arrow keys, `PgUp`/`PgDn`, panel buttons |
| Height / zoom in the view from above | scroll |
| Rotate / mirror / view from above | `R` / `M` / `T` |
| Pan the view from above | drag with the right button |

While holding the tool item (a stick by default):

| Action | Input |
|---|---|
| Switch mode (Area → Move → Paste → Delete) | `Ctrl` + scroll, or middle click |
| Area: corner 1 / corner 2 | left / right click |
| Move: move the placement / rotate it by 90° | left / right click |
| Paste / Delete: start (two clicks: ask, then confirm) / cancel | left / right click |
| Nudge the box or placement one block along your view | `Alt` + scroll |
| Use your own position instead of the targeted block | `Alt` + click |

Without the tool: right-click a ghost or an entity marker for easy place (when enabled), middle-click for pick block.

### Configuration

The menu (`K`) or **Mod Menu**: tabs Quick Access, Schematics, Placements, Area, Materials, Verifier, Settings. Settings live in `config/litematicaplus.json`, placements and selections in `config/litematica-plus-session.json`, the substitute material list in `config/litematica-plus-substitutes.json` (written atomically with a `.bak` backup). Easy place options show a tooltip on hover.

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API** `0.161.0+26.3`.
2. Disable Litematica, MaLiLib and EasyPlaceFix if they are installed: their features are already inside the mod.
3. Copy `litematica-plus-lts-1.1.0.jar` from `build/libs/` into `mods/`.
4. **Mod Menu** — optional, opens the mod's menu from the mod list.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/litematica-plus-lts-1.1.0.jar`.

Check your own schematics (read-only): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<schematics folder>"`. Automated in-game test (creates a world, places a schematic, checks highlights, paste, saving, easy place for 33 kinds of blocks, entities, the material helper, export, the placement editor and key binding, and takes screenshots): `.\gradlew.bat runClientGameTest`.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.

The easy place behaviour (server-side turn, tick pacing, follow-up clicks, note block tuning, sign and crafter data, substitutes, excavation guard, tool switch, entity placement, material export and the container helper) re-implements the feature set of [EasyPlaceFix](https://github.com/223225zzzkkk/easyplaceFix) (MIT) and its LTS_Server fork for this mod's own engine. No Litematica or MaLiLib code is used.
