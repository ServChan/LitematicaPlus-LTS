# Litematica Plus LTS

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.6-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Schematic holograms, area selection, verification, material lists, easy place and paste. A from-scratch, standalone replacement for Litematica that reads and writes `.litematic` files.

**Руководство пользователя с быстрым стартом:** [docs/USER_GUIDE_RU.md](docs/USER_GUIDE_RU.md) · **User guide with a quick start:** [docs/USER_GUIDE_EN.md](docs/USER_GUIDE_EN.md)

## Русский

### Что это

`Litematica Plus LTS` (mod id `litematicaplus`) — клиентский Fabric-мод для Minecraft 26.3, написанный с нуля вместо Litematica. Ему не нужны ни Litematica, ни MaLiLib: он сам читает и сохраняет `.litematic`, показывает схему полупрозрачной голограммой, сравнивает её с миром и помогает строить. Папка `schematics/` общая с Litematica, поэтому существующая библиотека схем открывается без переноса.

### Возможности

- **3D-превью схем**: во вкладках «Схемы» и «Размещения» выбранная схема показывается объёмной картинкой из настоящих моделей и текстур блоков; перетаскивание вращает, правой кнопкой — сдвиг, колесо — масштаб, двойной клик — сброс, стрелки и `+`/`-` работают с клавиатуры. Превью строится в фоновом потоке, вид по умолчанию кэшируется в `config/litematicaplus/previews/`, огромные схемы рисуются упрощёнными кубами;
- **Файлы**: чтение и запись `.litematic` (несколько регионов, отрицательные размеры, блок-сущности, сущности), импорт ванильных структур `.nbt`, Sponge `.schem` v2/v3 и старых MCEdit/Schematica `.schematic` (числовые ID блоков переводятся таблицей ванильного DataFixer), экспорт любой схемы в Sponge `.schem` и ванильный `.nbt` кнопками во вкладке «Схемы»; состояния блоков из старых версий проходят через ванильный DataFixer, неизвестные блоки пропускаются (не ставятся при вставке и не проверяются), а не роняют загрузку, и мод сообщает в чате, сколько их было;
- **Размещения**: сколько угодно схем в мире, перемещение, поворот на 90°, отражение по X/Z, блокировка; подрегионы можно отключать, сдвигать, поворачивать и отражать по отдельности (сдвиги подрегионов ходят и через Syncmatica); сетка размещений повторяет схему по осям X/Y/Z с автоматическим или заданным шагом, и призраки, проверка, материалы, лёгкая установка и вставка учитывают все копии; список хранится отдельно для каждого сервера и измерения;
- **Зоны для командной стройки**: вкладка «Зоны» режет размещение на части по X, Z и этажам; зону можно взять себе — тогда призраки, лёгкая установка и материалы ограничиваются ею, а у каждой зоны виден процент готовности по реальному миру. Кто какую зону взял, мод сообщает товарищам только командами — личными сообщениями `/msg` каждому, командным чатом `/teammsg` или своей командой (например, клановым чатом); команды, которые пишут всем (`say`, `me`, `tellraw` и подобные), запрещены, в общий чат мод не пишет никогда, а у товарищей с модом служебные строки скрываются. Сервер-посредник не нужен;
- **Место размещения в файле**: при сохранении во вкладке «Новая схема» можно включить «Записать место» и вписать координаты угла схемы с наименьшими X, Y и Z (по умолчанию угол выделения), поворот и отражение; у готового размещения кнопка «Записать место в файл» сохраняет в схему координаты, поворот, отражение, подрегионы и сетку, а Shift+клик — ещё замены блоков и разбивку на зоны. Если в выбранной схеме есть место, во вкладке «Схемы» появляются две кнопки: «На сохранённое место» (её же выбирает двойной клик) и «У меня». Записываются также измерение и сервер или мир; если они другие, мод предупреждает, но поставить всё равно можно. Место хранится в `.litematic` и `.schem` в отдельном блоке метаданных: Litematica такие файлы открывает как обычно, но при пересохранении в ней место пропадает;
- **Голограмма**: блоки-призраки с настраиваемой прозрачностью, сундуки и другие блоки со специальной моделью, жидкости; показываются только там, где блока ещё нет. По умолчанию призраки рисуются только в прогруженных чанках («Призраки только в прогруженных чанках» в настройках отрисовки), как в Litematica, чтобы с Voxy и шейдерами они не просвечивали сквозь дальний рельеф;
- **Проверка в реальном времени**: каждая секция схемы сравнивается с миром при любом изменении блока или загрузке чанка; цветная подсветка «нет блока», «не тот блок», «не то состояние», «лишний» с подписями в интерфейсе; по желанию — сквозь стены. Подсветка рисуется только на гранях, не закрытых соседним блоком, водой, льдом или стеклом, и только в прогруженных чанках. Дальний рельеф Voxy закрывает схему так же, как ближний: шейдерпаки вроде Complementary просят Voxy не писать глубину LOD в буфер игры, поэтому мод сам переносит эту глубину в свою копию буфера перед отрисовкой схемы, и призраки не просвечивают сквозь дальние горы ни в мире, ни на карте VoxyMap (если перенос не удался, схема над дальним рельефом просто не рисуется); результат сверки секции запоминается после выгрузки чанка, а с Voxy и VoxyMap непрогруженные части сверяются с блоками, которые сохранил Voxy («Сверять непрогруженное по Voxy»), поэтому процент и счётчики не меняются от того, куда вы летите. На сервере блок, закрытый со всех шести сторон, не считается ошибкой «не тот блок» («Скрытые блоки считать верными»): античит-подмена анти-ксрея иначе красит всё подземелье;
- **Умное сравнение**: свойства, которые меняются сами (листва `distance`/`persistent`, сила редстоуна, горящая печь, нажатая кнопка, выдвинутый поршень, возраст посадок), по умолчанию не считаются ошибкой; положение рычага, зажжённые свечи и костры, открытые двери сравниваются строго. С опцией «Засчитывать похожие блоки» (выключена по умолчанию) сверка принимает блок того же вида: камень вместо кварца, обычное стекло вместо крашеного, любые ступени с тем же поворотом; редстоун, рельсы, сыпучие блоки и блоки с содержимым сравниваются строго. Земля и дёрн по умолчанию взаимозаменяемы: земля на месте дёрна считается верной, лёгкая установка ставит землю, если дёрна нет, и ничего не выкапывает ради замены («Земля и дёрн взаимозаменяемы»);
- **Слои и границы X/Z**: все, один слой, этот и ниже/выше, диапазон — по любой оси; поверх слоя можно ограничить схему по X, по Z или по обеим осям (включительные «от» и «до», кнопки «Здесь» берут координату игрока, «По размещению» — границы выбранного размещения, «По выделению» — границы X/Z и диапазон слоёв по Y из рамки выделения инструмента). Призраки, подсветка, лёгкая установка, пипетка и Jade работают только внутри слоя и границ, а с опцией «Считать только видимую часть» (включена по умолчанию) прогресс, счётчики и список материалов тоже считают только эту часть. Всё это — во вкладке «Слои» (`F7`); границы сохраняются для каждого мира;
- **Материалы**: всего / осталось / есть в инвентаре (включая шалкеры), сортировка, экспорт в Excel (`.xlsx` с листами «Сводка», «Собрать», «Все материалы»), CSV, Markdown, JSON или в буфер обмена; файлы сохраняются в `litematica-plus/materials/`, ссылка на файл и папку появляется в чате; двойные плиты, свечи, слои снега, огурцы и вёдра жидкостей считаются правильно, цветок в горшке — это горшок и цветок, торт со свечой — торт и свеча, верхние половины дверей и кроватей не удваиваются. Содержимое эндер-сундука прибавляется к «Есть», если открыть его один раз после входа («Учитывать эндер-сундук в материалах»). Блок, который уже стоит, но повёрнут или в другом состоянии, материала не требует (кроме плит, свечей и слоёв, где состояние — это количество). Мод запоминает содержимое открытых за заход сундуков, бочек и шалкеров: клик по материалу во вкладке подсвечивает сквозь стены контейнеры, где он лежит, а запомненные контейнеры в пределах 64 блоков с материалами, которых не хватает в инвентаре, обводятся оранжевым сами («Запоминать открытые контейнеры», «Подсвечивать контейнеры с нужными материалами»);
- **Замены блоков**: во вкладке «Замены» любой блок схемы (кроме блоков с содержимым) можно заменить другим, похожие предлагаются первыми. Призраки, сверка, список материалов, лёгкая установка и вставка строят уже из замены; сам файл не меняется, замены хранятся у размещения и выключаются одной опцией;
- **Помощник материалов**: в сундуках, бочках, шалкерах, эндер-сундуках, вагонетках и лодках с сундуком, раздатчиках, воронках и у ослов, мулов и лам нужные выбранному размещению предметы подсвечены зелёным, шалкеры с ними — жёлтым. Сбоку от контейнера — панель материалов: чего ещё не хватает, сколько этого лежит здесь и сколько у вас; клик по строке забирает этот предмет, Shift+клик — всё такое из контейнера, правый клик передаёт количество в Stack Calculator. Кнопка «Взять нужное» (или горячая клавиша) забирает ровно столько, сколько не хватает: стак при необходимости делится несколькими кликами, шалкер с материалами берётся целиком, только если россыпью не хватило. «Сложить лишнее» возвращает в контейнер материалы сверх нужного, не трогая предмет в руке и слоты, закреплённые в Inventory Profiles Next. В подсказке предмета видно, сколько его ещё нужно, а в подсказке шалкера — какие нужные предметы внутри. Пока схемы скрыты или выбранное размещение выключено, схема о себе не сообщает: нет ни инфопанели с прогрессом и материалами, ни помощника у контейнеров, ни строк в подсказках («Скрытая схема о себе не сообщает»);
- **Jade**: если установлен Jade, при взгляде на блок схемы видно, поставлен ли он, совпадает ли со схемой и какое состояние ожидается; блок-призрак тоже показывается в окне Jade;
- **Лёгкая установка** со встроенными возможностями EasyPlaceFix: правая кнопка по призраку ставит нужный блок, удержание кнопки ведёт установку вдоль прицела. Перед кликом мод перебирает направления взгляда и точки клика, моделирует установку и выбирает вариант, дающий состояние из схемы; нужный поворот отправляется серверу, а камера остаётся на месте. Темп считается в игровых тиках (пресеты «Баланс» 2, «Осторожно» 4, «Быстро» 1, «Свой»), есть случайный тик. После установки мод доводит состояние: задержка повторителя, режим компаратора, рычаг, открытые люки, двери и калитки, двойные плиты, свечи, цветок в горшке, высота ноты (очередь кликов с панелью прогресса); текст табличек берётся из схемы, у крафтера отключаются нужные слоты; по опциям свечи и костры зажигаются огнивом или огненным шаром и тушатся (костёр — лопатой, свеча — пустой рукой), а блоки заливаются водой из ведра или осушаются пустым ведром. Дополнительно: наблюдатель ждёт блок перед собой, список замен материалов, защита при раскопках, автосмена инструмента (внутри включённого размещения, в том числе без лёгкой установки), замена вариантов земли и выкапывание неверных блоков;
- **Сущности схемы**: рамки и светящиеся рамки с предметом и поворотом, картины и стойки для брони с бронёй отмечены рамкой-маркером; наведитесь и нажмите использование;
- **Без лишних блоков**: пока включена лёгкая установка, блок из руки нельзя поставить вручную внутри размещения и в двух блоках от него, а после установки по призраку удержание кнопки больше не ставит второй блок рядом; отдельная «Защита от ошибок» работает и без лёгкой установки. Правый клик лопатой, мотыгой или топором по блоку схемы отменяется, если превратил бы его в то, чего нет в схеме (дёрн в тропинку, землю в грядку, бревно в обтёсанное) — «Не портить блоки схемы инструментами»;
- **Карта VoxyMap**: с установленным VoxyMap размещения видны на 3D-карте рамкой с названием и процентом, в панели объектов и на миникарте; призраки на карте рисуются вокруг центра обзора карты; слой «Схемы (Litematica Plus)» в настройках VoxyMap выключает всё это;
- **Axiom** (необязательно): блюпринты Axiom `.bp` читаются как обычные схемы, папка блюпринтов Axiom видна прямо во вкладке «Схемы», а кнопка «В .bp» кладёт копию любой схемы в браузер блюпринтов Axiom (без установленного Axiom — рядом со схемой). С установленным Axiom размещение передаётся во вставку Axiom ровно на своё место (с поворотом, подрегионами, сеткой, заменами, текущим слоем и границами X/Z; двигать гизмо и подтверждать — в Axiom, отмена — историей Axiom), регионы размещения становятся выделением Axiom, выделение Axiom превращается в рамку для новой схемы и обратно, а буфер Axiom сохраняется в `.litematic` вместе с данными контейнеров и сущностями. Пока открыт редактор Axiom, инструмент, лёгкая установка и горячие клавиши мода не перехватывают ввод (опция «Уступать редактору Axiom»);
- **Редактор размещения**: прозрачный экран с панелью справа, схема видна в мире и двигается сразу. Координаты X/Y/Z вводятся с клавиатуры, `Ctrl+V` вставляет три числа; перетаскивание мышью, стрелки, шаг 1/5/16/64, поворот, отражение, вид сверху с панорамой и масштабом;
- **Горячие клавиши**: вкладка «Клавиши» в настройках мода — назначение клавиши или кнопки мыши, подсветка конфликтов, `Esc` снимает назначение;
- **Пипетка**: средняя кнопка по призраку берёт нужный предмет (в выживании — из инвентаря, в творческом — выдаёт);
- **Защита слотов**: отмеченные слоты хотбара (Настройки → Установка → «Защита слотов») лёгкая установка, пипетка и автосмена инструмента только выбирают, но не кладут в них другие предметы — инструменты остаются на своих местах;
- **Подсветка того, что можно поставить**: ближайшие недостающие и неверные блоки, предмет для которых есть в инвентаре, обводятся рамкой (опция и горячая клавиша);
- **Инфопанель**: перетаскивается в любое место экрана или ставится в угол (Настройки → Панель → «Положение инфопанели...»);
- **Выделение и сохранение**: области из нескольких боксов, сохранение в `.litematic`; в одиночной игре сохраняются полные данные блок-сущностей и сущности;
- **Вставка, заливка, замена и удаление** (творческий режим): вставка учитывает слой и границы X/Z; в одиночной игре всё идёт напрямую, вставка переносит содержимое сундуков и сущности схемы (повторная вставка не плодит дубликаты); режимы инструмента «Заливка» и «Замена» заполняют выделение блоком или меняют один блок на другой (блок выбирается Alt + средней кнопкой по блоку в мире, «что заменять» — Alt + Shift + средней кнопкой); на сервере — командами `/setblock` и `/fill ... strict` с ограничением скорости (по умолчанию 8 команд за тик, без прав оператора — одна команда примерно в секунду, чтобы сервер не кикнул за спам). Оба действия требуют подтверждения вторым кликом, удаление ограничено 64 млн блоков;
- **Производительность**: схема на 4,4 млн блоков загружается из файла за ~0,1 с, разворачивается в мир за ~0,2 с и полностью проверяется и мешится за ~1,7 с в фоновых потоках; FPS при этом держался на лимите 120 (замер на RTX 5080 автотестом из раздела «Сборка»). Главный поток только копирует секции мира в пределах бюджета времени; сравнение и сборка мешей идут в фоне, готовые меши лежат в постоянных GPU-буферах и рисуются одним проходом.

Что пока не перенесено из Litematica:

- вставка сущностей на сервере (командами; в выживании рамки, картины и стойки ставятся лёгкой установкой), проекты схем;
- на сервере вставка не переносит содержимое контейнеров (ограничение длины команды);
- с шейдерами Iris призраки рисуются поверх готового кадра, поэтому на них не действуют тени, блум и тональная коррекция пакета; сундуки и другие блоки со специальной моделью рисуются игрой как обычные, непрозрачными;
- в виде сверху рельеф виден только в прогруженных вокруг игрока чанках; с шейдерами туман пакета отодвигается через значение `far` от Iris, и в отдельных пакетах рельеф всё равно может тонуть в дымке;
- материалы для сущностей в список не входят.

Совместимость проверена автотестом в игре: без модов рендера и с Sodium `0.9.3-alpha.1` + Iris `1.11.6` с пакетом Complementary Reimagined r5.9.3; панель материалов — вместе с Inventory Profiles Next 2.3.8, Stack Calculator 1.2.0 и Jade 26.3.3 (окно Jade в автотесте не проверяется). Тест идёт на встроенном сервере одиночной игры: на настоящем многопользовательском сервере с античитом мод не проверялся, поэтому темп «Осторожно» и отключение кликов по воздуху оставлены в настройках.

### Управление

Клавиши назначаются в меню мода (Настройки → Клавиши) или в Настройки → Управление → Litematica Plus.

| Действие | Клавиша по умолчанию |
|---|---|
| Открыть меню | `H` |
| Вкладка «Слои» и границы X/Z | `F7` |
| Включить/выключить границы X/Z | не назначена |
| Редактор размещения в мире | не назначена |
| Показать/скрыть схемы, лёгкая установка, инструмент | не назначены |
| Следующий режим инструмента, режим слоёв, слой выше/ниже | не назначены |
| Установка удержанием, защита при раскопках, автосмена инструмента, ломать неверные блоки, замена материалов | не назначены |
| Взять материалы из открытого контейнера | не назначена |
| Открыть список материалов | не назначена |
| Подсветка того, что можно поставить | не назначена |

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

Меню (`H`) или **Mod Menu**: вкладки «Быстрый доступ», «Схемы», «Размещения», «Слои», «Новая схема» (сохранение постройки из мира), «Материалы», «Замены», «Проверка», «Настройки». Настройки лежат в `config/litematicaplus.json`, размещения и выделения — в `config/litematica-plus-session.json`, список замен материалов — в `config/litematica-plus-substitutes.json` (файлы пишутся атомарно, с резервной копией `.bak`). У параметров лёгкой установки есть подсказки при наведении.

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API** `0.161.0+26.3`.
2. Отключите Litematica, MaLiLib и EasyPlaceFix, если они стоят: их функции уже внутри мода. Мод ставится вместо Litematica, а не вместе с ней: если обе лежат в `mods/`, при запуске поверх главного меню появится предупреждение.
3. Скопируйте `litematica-plus-lts-1.0.6.jar` из `build/libs/` в папку `mods/`.
4. **Mod Menu** — опционально, открывает меню мода из списка модов.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/litematica-plus-lts-1.0.6.jar`.

Рядом собирается отдельная версия с принтером — `build/libs/litematica-plus-lts-1.0.6-printer.jar` (задача `printerJar`). Это тот же мод с тем же id, поэтому в `mods/` кладётся только один из двух файлов. Принтер сам ставит недостающие блоки схемы в радиусе 1–6 блоков вокруг игрока, без кликов, и берёт настройки лёгкой установки: темп, поворот для сервера, доведение состояний, выкапывание неверных блоков, замены, слои и границы. Включается в Настройки → «Принтер» или клавишей «Вкл/выкл принтер» (по умолчанию не назначена), настройки — `config/litematicaplus-printer.json`. Многие серверы запрещают принтеры: обычная версия кода принтера не содержит. Игровой тест принтера: `.\gradlew.bat runClientGameTest -PprinterOnly`.

Проверка на своих схемах (только чтение): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<папка schematics>"`. Автоматический тест в игре (создаёт мир, размещает схему, проверяет подсветку, вставку, сохранение, лёгкую установку 33 видов блоков, сущности, помощник материалов, экспорт, редактор размещения, назначение клавиш и делает скриншоты): `.\gradlew.bat runClientGameTest`.

---

## English

### What It Is

`Litematica Plus LTS` (mod id `litematicaplus`) is a client-side Fabric mod for Minecraft 26.3, written from scratch as a replacement for Litematica. It needs neither Litematica nor MaLiLib: it reads and writes `.litematic` itself, shows a schematic as a translucent hologram, compares it with the world, and helps you build. It shares the `schematics/` folder with Litematica, so an existing library opens without any migration.

### Features

- **3D schematic previews**: the Schematics and Placements tabs show the selected schematic as a 3D picture built from the real block models and textures; drag to rotate, right drag to pan, scroll to zoom, double-click to reset, and the arrow keys and `+`/`-` work from the keyboard. Previews are built on a background thread, the default view is cached in `config/litematicaplus/previews/`, and huge schematics are drawn as simplified cubes;
- **Files**: reads and writes `.litematic` (multiple regions, negative sizes, block entities, entities), imports vanilla structure `.nbt`, Sponge `.schem` v2/v3 and legacy MCEdit/Schematica `.schematic` (numeric block IDs go through the vanilla DataFixer table), and exports any schematic to Sponge `.schem` or vanilla `.nbt` with buttons in the Schematics tab; block states from older versions go through the vanilla DataFixer, and unknown blocks are skipped (not pasted and not verified) instead of failing the load, with a chat message saying how many there were;
- **Placements**: any number of schematics per world, with move, 90° rotation, X/Z mirror and lock; sub-regions can be toggled, moved, rotated and mirrored one by one (sub-region shifts also travel through Syncmatica); a placement grid repeats the schematic along X/Y/Z with an automatic or custom step, and ghosts, verification, materials, easy place and paste cover every copy; the list is stored per server and dimension;
- **Placement stored in the file**: when saving on the New schematic tab, turn on Save spot and type the coordinates of the schematic's corner with the lowest X, Y and Z (the selection corner by default), the turn and the mirror; for an existing placement, Save spot into file stores its coordinates, turn, mirror, subregions and grid, and Shift+click also stores block replacements and zones. When the selected schematic has a spot, the Schematics tab shows two buttons: At the saved spot (also chosen by a double click) and At my position. The dimension and the server or world are saved too; if they differ, the mod warns you but still lets you place it. The spot lives in a separate metadata block of `.litematic` and `.schem` files: Litematica opens them as usual, but drops the spot if it saves the file again;
- **Hologram**: ghost blocks with adjustable opacity, chests and other blocks with a special model, and fluids; drawn only where the block is not there yet. By default ghosts are drawn only in loaded chunks ("Ghosts only in loaded chunks" under the rendering settings), as Litematica does, so with Voxy and shaders they do not show through distant terrain;
- **Live verification**: every schematic section is compared with the world whenever a block changes or a chunk loads; colored highlights for "missing", "wrong block", "wrong state" and "extra", always labeled in the UI; optionally visible through walls. Highlights are drawn only on faces not covered by a neighbouring block, water, ice or glass, and only in loaded chunks. Voxy's distant terrain hides the schematic just like nearby terrain: shader packs such as Complementary ask Voxy not to write its LOD depth into the game's depth buffer, so the mod copies that depth into its own depth buffer before drawing the schematic, and ghosts no longer show through distant mountains in the world or on the VoxyMap map (if the copy fails, the schematic is simply not drawn over distant terrain); a section's verdict is remembered after its chunk unloads, and with Voxy and VoxyMap the unloaded parts are compared against the blocks Voxy has stored ("Check unloaded chunks with Voxy"), so the percentage and counters do not change depending on where you fly. On a server a block enclosed on all six sides is not counted as a "wrong block" error ("Hidden blocks count as correct"): an anti-xray's decoy blocks would otherwise paint the whole underground red;
- **Smart comparison**: properties that change by themselves (leaf `distance`/`persistent`, redstone power, a lit furnace, a pressed button, an extended piston, crop age) are not counted as errors by default; lever position, lit candles and campfires, and open doors are compared strictly. With "Count similar blocks as correct" (off by default) the verifier accepts a block of the same kind: stone for quartz, plain glass for stained, any stairs with the same facing; redstone parts, rails, falling blocks and blocks with contents stay strict. Dirt and grass are interchangeable by default: dirt where the schematic has grass counts as correct, easy place puts dirt down when you have no grass, and nothing digs one out for the other ("Dirt and grass are interchangeable");
- **Layers and X/Z bounds**: all, single layer, this and below/above, range — on any axis; on top of the layer the schematic can be limited along X, Z or both (inclusive from and to; Here buttons take the player's coordinate, Fit placement takes the selected placement's bounds, Fit selection takes the X/Z bounds and a Y layer range from the tool's area selection). Ghosts, highlights, easy place, pick block and Jade work only inside the layer and the bounds, and with "Count only the visible part" (on by default) progress, counters and the material list count only that part too. All of it lives on the Layers tab (`F7`); bounds are kept per world;
- **Materials**: total / still needed / in inventory (shulker boxes included), sorting, export to Excel (`.xlsx` with Summary, To gather and All materials sheets), CSV, Markdown, JSON or the clipboard; files go to `litematica-plus/materials/` and a link to the file and the folder appears in chat; double slabs, candles, snow layers, sea pickles and fluid buckets are counted correctly, a potted flower counts as a pot plus the flower, a candle cake as a cake plus a candle, and upper door halves and bed heads are not double-counted. Ender chest contents are added to Have once you open it after joining ("Count the ender chest in materials"). A block that is already there but turned the wrong way or in another state needs no material (except slabs, candles and layers, where the state is the count). The mod remembers what is in chests, barrels and shulker boxes you open during the session: clicking a material in the tab lights up the containers holding it through walls, and remembered containers within 64 blocks holding a material you lack in your inventory get an orange outline on their own ("Remember opened containers", "Highlight containers with missing materials");
- **Block replacements**: on the Replacements tab any schematic block (except blocks with contents) can be swapped for another, similar blocks first. Ghosts, the verifier, the material list, easy place and paste all build from the replacement; the file is not changed, replacements are kept per placement and one option turns them off;
- **Material helper**: in chests, barrels, shulker boxes, ender chests, chest minecarts and chest boats, dispensers, hoppers and on donkeys, mules and llamas the items the selected placement still needs are highlighted green and shulker boxes holding them yellow. A material panel beside the container lists what is still missing, how much of it is here and how much you carry; click a line to take that item, Shift+click to take all of it from the container, right click to send the amount to Stack Calculator. Take needed (or the hotkey) takes exactly the shortfall, splitting a stack with a few clicks when needed, and takes a shulker box with materials whole only when loose items are not enough. Put back extra returns materials beyond what is needed, leaving the item in your hand and slots locked in Inventory Profiles Next alone. Item tooltips show how many more are needed, and shulker box tooltips list the needed items inside. While schematics are hidden or the selected placement is disabled, the schematic stays quiet: no info panel with progress and materials, no helper at containers, no tooltip lines ("Hidden schematics stay quiet");
- **Jade**: with Jade installed, looking at a schematic block shows whether it is placed, whether it matches and which state is expected; ghost blocks show up in the Jade window too;
- **Easy place** with the EasyPlaceFix feature set built in: right-click a ghost to place the right block, hold the button to keep placing along your aim. Before clicking, the mod tries look directions and click points, simulates the placement and picks the one that yields the schematic state; the needed turn is sent to the server while your camera stays still. Pacing is counted in game ticks (presets Balanced 2, Safe 4, Fast 1, Custom) with an optional random tick. After placing it finishes the state: repeater delay, comparator mode, lever, open trapdoors, doors and gates, double slabs, candles, potted flowers, note pitch (a click queue with a progress line on the HUD); sign text comes from the schematic and crafter slots are disabled as saved; optionally candles and campfires are lit with flint and steel or a fire charge and put out (campfires with a shovel, candles with an empty hand), and blocks are waterlogged with a water bucket or drained with an empty one. Also: observers wait for the block they watch, a substitute material list, excavation guard, auto tool switch (inside an enabled placement, with or without easy place), ground variant swap and mining out wrong blocks;
- **Schematic entities**: item frames and glow item frames with their item and rotation, paintings and armor stands with armor get an outline marker; aim and use;
- **No stray blocks**: while easy place is on, a block in hand cannot be placed by hand inside a placement or within two blocks of it, and after placing on a ghost the held button no longer puts a second block next to it; the separate Placement guard also works without easy place. A right-click with a shovel, hoe or axe on a schematic block is cancelled when it would turn the block into something the schematic does not ask for (grass into a path, dirt into farmland, a log into a stripped log) - "Do not spoil schematic blocks with tools";
- **VoxyMap**: with VoxyMap installed, placements appear on the 3D map as an outlined box with name and progress, in the objects panel and on the minimap; on the map the ghosts are drawn around the map's view centre; the "Schematics (Litematica Plus)" layer in VoxyMap's settings turns all of it off;
- **Axiom** (optional): Axiom `.bp` blueprints load like any other schematic, the Axiom blueprints folder shows up right in the Schematics tab, and the "To .bp" button drops a copy of any schematic into the Axiom blueprint browser (next to the schematic when Axiom is not installed). With Axiom installed, a placement is handed to Axiom's paste exactly where it stands (with rotation, sub-regions, grid, replacements, the current layer and X/Z limits; move the gizmo and confirm in Axiom, undo through Axiom's history), placement regions become the Axiom selection, the Axiom selection becomes a box for a new schematic and back, and the Axiom clipboard saves to `.litematic` with container data and entities. While the Axiom editor is open, the tool, easy place and the mod's hotkeys stay out of the way ("Step aside in the Axiom editor");
- **Placement editor**: a see-through screen with a side panel, so the schematic stays visible in the world and moves at once. X/Y/Z are typed on the keyboard and `Ctrl+V` pastes three numbers; mouse drag, arrow keys, step 1/5/16/64, rotate, mirror, and a view from above with pan and zoom;
- **Hotkeys**: a Hotkeys tab in the mod's settings binds a key or mouse button, marks conflicts, and `Esc` clears a binding;
- **Pick block**: middle-click a ghost to take the needed item (from the inventory in survival, granted in creative);
- **Locked slots**: easy place, pick block and auto tool switch only select the marked hotbar slots (Settings → Easy place → Locked slots) and never put other items into them, so tools stay where they are;
- **Placeable highlight**: the nearest missing and wrong blocks whose item is in your inventory get an outline (option and hotkey);
- **Info panel**: drag it anywhere on the screen or snap it to a corner (Settings → HUD → Info panel position...);
- **Zones for team builds**: the Zones tab cuts a placement into parts along X, Z and floors; taking a zone limits ghosts, easy place and materials to it, and every zone shows its progress against the real world. Who took which zone is sent to teammates only through commands — private `/msg` to each, team chat `/teammsg`, or a custom command such as a clan chat; commands that write to everyone (`say`, `me`, `tellraw` and the like) are refused, the mod never writes to the public chat, and the service lines are hidden for teammates with the mod. No relay server is needed;
- **Selection and saving**: areas made of several boxes, saved as `.litematic`; in singleplayer the full block entity data and entities are saved;
- **Paste, fill, replace and delete** (creative): paste follows the layer and X/Z bounds; in singleplayer everything is direct, and paste brings chest contents and the schematic's entities (pasting again does not duplicate them); the Fill and Replace tool modes fill the selection with a block or swap one block for another (pick the block with Alt + middle click on a block in the world, what to replace with Alt + Shift + middle click); on a server through rate-limited `/setblock` and `/fill ... strict` commands (8 commands per tick by default, and about one command per second without operator rights so the server does not kick for spam). Both actions need a confirming second click, and delete is limited to 64 million blocks;
- **Performance**: a 4.4 million block schematic loads from disk in ~0.1 s, is laid out in the world in ~0.2 s, and is fully verified and meshed in ~1.7 s on background threads while the frame rate stayed at its 120 FPS cap (measured on an RTX 5080 by the automated test described under Building). The main thread only copies world sections within a time budget; comparison and meshing run in the background, and finished meshes live in persistent GPU buffers drawn in a single pass.

Not ported from Litematica yet:

- pasting entities on a server (through commands; in survival, frames, paintings and armor stands are placed by easy place), schematic projects;
- server-side paste does not carry container contents (command length limit);
- with Iris shaders the ghosts are drawn on top of the finished frame, so a pack's shadows, bloom and tone mapping do not apply to them; chests and other blocks with a special model are drawn by the game as ordinary, opaque blocks;
- in the view from above, terrain is visible only in the chunks loaded around the player; with shaders the pack's fog is pushed back through the `far` value Iris provides, and some packs may still hide the terrain in haze;
- materials for entities are not part of the material list.

Compatibility was checked by the automated in-game test: without render mods, and with Sodium `0.9.3-alpha.1` + Iris `1.11.6` running the Complementary Reimagined r5.9.3 pack; the material panel together with Inventory Profiles Next 2.3.8, Stack Calculator 1.2.0 and Jade 26.3.3 (the Jade window itself is not checked by the test). The test runs on the integrated singleplayer server: the mod has not been tried on a real multiplayer server with an anti-cheat, which is why the Safe pace and the switch for air clicks stay in the settings.

### Controls

Keys are bound in the mod's menu (Settings → Hotkeys) or in Options → Controls → Litematica Plus.

| Action | Default key |
|---|---|
| Open the menu | `H` |
| Layers tab and X/Z bounds | `F7` |
| Toggle X/Z bounds | unbound |
| Placement editor in the world | unbound |
| Show or hide schematics, easy place, tool item | unbound |
| Next tool mode, layer mode, layer up/down | unbound |
| Hold to keep placing, excavation guard, auto tool switch, break wrong blocks, substitute materials | unbound |
| Take materials from the open container | unbound |
| Open the material list | unbound |
| Highlight placeable blocks | unbound |

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

The menu (`H`) or **Mod Menu**: tabs Quick Access, Schematics, Placements, Layers, New schematic (saving a build from the world), Materials, Replacements, Verifier, Settings. Settings live in `config/litematicaplus.json`, placements and selections in `config/litematica-plus-session.json`, the substitute material list in `config/litematica-plus-substitutes.json` (written atomically with a `.bak` backup). Easy place options show a tooltip on hover.

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API** `0.161.0+26.3`.
2. Disable Litematica, MaLiLib and EasyPlaceFix if they are installed: their features are already inside the mod. The mod replaces Litematica rather than going alongside it: if both are in `mods/`, a warning appears over the title screen at startup.
3. Copy `litematica-plus-lts-1.0.6.jar` from `build/libs/` into `mods/`.
4. **Mod Menu** — optional, opens the mod's menu from the mod list.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/litematica-plus-lts-1.0.6.jar`.

A separate printer edition is built next to it: `build/libs/litematica-plus-lts-1.0.6-printer.jar` (task `printerJar`). It is the same mod with the same id, so put only one of the two files into `mods/`. The printer places missing schematic blocks within 1–6 blocks of the player by itself, without clicking, using the easy place settings: pace, server rotation, finishing states, breaking wrong blocks, substitutes, layers and bounds. Turn it on in Settings → Printer or with the "Toggle printer" key (unbound by default); its settings live in `config/litematicaplus-printer.json`. Many servers forbid printers, so the standard edition contains no printer code. Printer game test: `.\gradlew.bat runClientGameTest -PprinterOnly`.

Check your own schematics (read-only): `.\gradlew.bat test --tests "*SampleLibraryTest*" "-PsampleDir=<schematics folder>"`. Automated in-game test (creates a world, places a schematic, checks highlights, paste, saving, easy place for 33 kinds of blocks, entities, the material helper, export, the placement editor and key binding, and takes screenshots): `.\gradlew.bat runClientGameTest`.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.

The easy place behaviour (server-side turn, tick pacing, follow-up clicks, note block tuning, sign and crafter data, substitutes, excavation guard, tool switch, entity placement, material export and the container helper) re-implements the feature set of [EasyPlaceFix](https://github.com/223225zzzkkk/easyplaceFix) (MIT) and its LTS_Server fork for this mod's own engine. No Litematica or MaLiLib code is used.
