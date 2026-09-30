# Litematica Plus LTS

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Schematic holograms, area selection, verification, material lists, easy place and paste. A from-scratch, standalone replacement for Litematica that reads and writes `.litematic` files.

## Русский

### Что это

`Litematica Plus LTS` (mod id `litematicaplus`) — клиентский Fabric-мод для Minecraft 26.3, написанный с нуля вместо Litematica. Ему не нужны ни Litematica, ни MaLiLib: он сам читает и сохраняет `.litematic`, показывает схему полупрозрачной голограммой, сравнивает её с миром и помогает строить. Папка `schematics/` общая с Litematica, поэтому существующая библиотека схем открывается без переноса.

### Возможности

- **Файлы**: чтение и запись `.litematic` (несколько регионов, отрицательные размеры, блок-сущности, сущности), импорт ванильных структур `.nbt` и Sponge `.schem` v2/v3; состояния блоков из старых версий проходят через ванильный DataFixer, неизвестные блоки становятся воздухом, а не роняют загрузку;
- **Размещения**: сколько угодно схем в мире, перемещение, поворот на 90°, отражение по X/Z, блокировка, отключение отдельных подрегионов; список хранится отдельно для каждого сервера и измерения;
- **Голограмма**: блоки-призраки с настраиваемой прозрачностью, сундуки и другие блоки со специальной моделью, жидкости; показываются только там, где блока ещё нет;
- **Проверка в реальном времени**: каждая секция схемы сравнивается с миром при любом изменении блока или загрузке чанка; цветная подсветка «нет блока», «не тот блок», «не то состояние», «лишний» с подписями в интерфейсе; по желанию — сквозь стены;
- **Умное сравнение**: изменчивые свойства (листва `distance`/`persistent`, сила редстоуна, `lit`, `powered`, возраст посадок) по умолчанию не считаются ошибкой;
- **Слои**: все, один слой, этот и ниже/выше, диапазон — по любой оси;
- **Материалы**: всего / осталось / есть в инвентаре (включая шалкеры), сортировка, копирование списка в буфер обмена; двойные плиты, свечи, слои снега, огурцы и вёдра жидкостей считаются правильно, верхние половины дверей и кроватей не удваиваются;
- **Лёгкая установка**: правый клик по призраку ставит нужный блок. Перед кликом мод моделирует установку и кликает только так, чтобы ориентация совпала со схемой; если для этого нужно повернуться — скажет об этом, а не поставит блок боком;
- **Защита от ошибок**: запрещает ставить блок туда, где по схеме должен быть другой;
- **Пипетка**: средняя кнопка по призраку берёт нужный предмет (в выживании — из инвентаря, в творческом — выдаёт);
- **Выделение и сохранение**: области из нескольких боксов, сохранение в `.litematic`; в одиночной игре сохраняются полные данные блок-сущностей и сущности;
- **Вставка и удаление** (творческий режим): в одиночной игре — напрямую, с содержимым сундуков; на сервере — командами `/setblock` и `/fill ... strict` с ограничением скорости;
- **Производительность**: схема на 4,4 млн блоков загружается из файла за ~0,1 с, разворачивается в мир за ~0,2 с и полностью проверяется и мешится за ~1,7 с в фоновых потоках; FPS при этом держался на лимите 120 (замер на RTX 5080 автотестом из раздела «Сборка»). Главный поток только копирует секции мира в пределах бюджета времени; сравнение и сборка мешей идут в фоне, готовые меши лежат в постоянных GPU-буферах и рисуются одним проходом.

Что пока не перенесено из Litematica:

- вставка сущностей, старый формат `.schematic` (MCEdit), сдвиг отдельных подрегионов, проекты схем, режимы инструмента «заливка/замена», сетка размещений;
- на сервере вставка не переносит содержимое контейнеров (ограничение длины команды);
- совместная работа с Sodium/Iris на 26.3 не проверялась; мод рисует собственным проходом поверх кадра и не трогает рендер чанков.

### Управление

| Действие | Клавиша по умолчанию | Переназначение |
|---|---|---|
| Открыть меню | `K` | Options → Controls → Litematica Plus |
| Показать/скрыть схемы | не назначена | там же |
| Лёгкая установка вкл/выкл | не назначена | там же |
| Следующий режим инструмента | не назначена | там же |
| Режим слоёв, слой выше/ниже | не назначены | там же |

С инструментом в руке (по умолчанию палка):

| Действие | Ввод |
|---|---|
| Сменить режим (Область → Перемещение → Вставка → Удаление) | `Ctrl` + колесо или средняя кнопка |
| Область: угол 1 / угол 2 | левая / правая кнопка |
| Перемещение: перенести размещение / повернуть на 90° | левая / правая кнопка |
| Вставка / Удаление: начать / отменить | левая / правая кнопка |
| Сдвинуть бокс или размещение на 1 блок по взгляду | `Alt` + колесо |
| Взять свою позицию вместо блока под прицелом | `Alt` + клик |

Без инструмента: правая кнопка по призраку — лёгкая установка (если включена), средняя кнопка — пипетка.

### Настройки

Меню (`K`) или **Mod Menu**: вкладки «Быстрый доступ», «Схемы», «Размещения», «Область», «Материалы», «Проверка», «Настройки». Настройки лежат в `config/litematicaplus.json`, размещения и выделения — в `config/litematica-plus-session.json` (оба файла пишутся атомарно, с резервной копией `.bak`).

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API** `0.161.0+26.3`.
2. Скопируйте `litematica-plus-lts-1.0.0.jar` из `build/libs/` в папку `mods/`.
3. **Mod Menu** — опционально, открывает меню мода из списка модов.
4. Оригинальные Litematica и MaLiLib не нужны.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/litematica-plus-lts-1.0.0.jar`.

Проверка на своих схемах (только чтение): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<папка schematics>"`. Автоматический тест в игре (создаёт мир, размещает схему, проверяет подсветку, вставку, сохранение, лёгкую установку и делает скриншоты): `.\gradlew.bat runClientGameTest`.

---

## English

### What It Is

`Litematica Plus LTS` (mod id `litematicaplus`) is a client-side Fabric mod for Minecraft 26.3, written from scratch as a replacement for Litematica. It needs neither Litematica nor MaLiLib: it reads and writes `.litematic` itself, shows a schematic as a translucent hologram, compares it with the world, and helps you build. It shares the `schematics/` folder with Litematica, so an existing library opens without any migration.

### Features

- **Files**: reads and writes `.litematic` (multiple regions, negative sizes, block entities, entities), imports vanilla structure `.nbt` and Sponge `.schem` v2/v3; block states from older versions go through the vanilla DataFixer, and unknown blocks become air instead of failing the load;
- **Placements**: any number of schematics per world, with move, 90° rotation, X/Z mirror, lock, and per-sub-region toggles; the list is stored per server and dimension;
- **Hologram**: ghost blocks with adjustable opacity, chests and other blocks with a special model, and fluids; drawn only where the block is not there yet;
- **Live verification**: every schematic section is compared with the world whenever a block changes or a chunk loads; colored highlights for "missing", "wrong block", "wrong state" and "extra", always labeled in the UI; optionally visible through walls;
- **Smart comparison**: volatile properties (leaf `distance`/`persistent`, redstone power, `lit`, `powered`, crop age) are not counted as errors by default;
- **Layers**: all, single layer, this and below/above, range — on any axis;
- **Materials**: total / still needed / in inventory (shulker boxes included), sorting, copy the list to the clipboard; double slabs, candles, snow layers, sea pickles and fluid buckets are counted correctly, and upper door halves and bed heads are not double-counted;
- **Easy place**: right-click a ghost to place the right block. Before clicking, the mod simulates the placement and only clicks in a way that produces the schematic's orientation; if you need to turn first, it tells you instead of placing the block sideways;
- **Placement guard**: refuses to place a block where the schematic expects a different one;
- **Pick block**: middle-click a ghost to take the needed item (from the inventory in survival, granted in creative);
- **Selection and saving**: areas made of several boxes, saved as `.litematic`; in singleplayer the full block entity data and entities are saved;
- **Paste and delete** (creative): direct in singleplayer, including chest contents; on a server through rate-limited `/setblock` and `/fill ... strict` commands;
- **Performance**: a 4.4 million block schematic loads from disk in ~0.1 s, is laid out in the world in ~0.2 s, and is fully verified and meshed in ~1.7 s on background threads while the frame rate stayed at its 120 FPS cap (measured on an RTX 5080 by the automated test described under Building). The main thread only copies world sections within a time budget; comparison and meshing run in the background, and finished meshes live in persistent GPU buffers drawn in a single pass.

Not ported from Litematica yet:

- entity pasting, the legacy `.schematic` (MCEdit) format, moving individual sub-regions, schematic projects, the fill/replace tool modes, grid placements;
- server-side paste does not carry container contents (command length limit);
- running alongside Sodium/Iris on 26.3 has not been tested; the mod draws in its own pass on top of the frame and does not touch chunk rendering.

### Controls

| Action | Default key | Rebind |
|---|---|---|
| Open the menu | `K` | Options → Controls → Litematica Plus |
| Show or hide schematics | unbound | same place |
| Toggle easy place | unbound | same place |
| Next tool mode | unbound | same place |
| Layer mode, layer up/down | unbound | same place |

While holding the tool item (a stick by default):

| Action | Input |
|---|---|
| Switch mode (Area → Move → Paste → Delete) | `Ctrl` + scroll, or middle click |
| Area: corner 1 / corner 2 | left / right click |
| Move: move the placement / rotate it by 90° | left / right click |
| Paste / Delete: start / cancel | left / right click |
| Nudge the box or placement one block along your view | `Alt` + scroll |
| Use your own position instead of the targeted block | `Alt` + click |

Without the tool: right-click a ghost for easy place (when enabled), middle-click for pick block.

### Configuration

The menu (`K`) or **Mod Menu**: tabs Quick Access, Schematics, Placements, Area, Materials, Verifier, Settings. Settings live in `config/litematicaplus.json`; placements and selections in `config/litematica-plus-session.json` (both written atomically with a `.bak` backup).

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API** `0.161.0+26.3`.
2. Copy `litematica-plus-lts-1.0.0.jar` from `build/libs/` into `mods/`.
3. **Mod Menu** — optional, opens the mod's menu from the mod list.
4. The original Litematica and MaLiLib are not needed.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/litematica-plus-lts-1.0.0.jar`.

Check your own schematics (read-only): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<schematics folder>"`. Automated in-game test (creates a world, places a schematic, checks highlights, paste, saving and easy place, and takes screenshots): `.\gradlew.bat runClientGameTest`.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.
