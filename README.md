# V1zir Visuals

Wizualny mod kliencki dla **Minecraft 1.21.11 (Fabric)**: shaderowe niebo z presetami, własne
animowane menu główne, Click GUI w stylu CatLean / Nursultan, HUD i efekty PvP. Cały interfejs
jest rysowany przez własny renderer: zaokrąglone kształty liczone jako SDF (idealnie gładkie
krawędzie w każdej skali GUI), rozmyte "szklane" panele i własne fonty renderowane jako
**signed distance field** – ostre przy każdym rozmiarze.

## Funkcje

### Menu główne
- **Ekran blokady** jak w telefonie: duży zegar, data i powitanie zależne od pory dnia
  („Dzień dobry”, „Dobry wieczór”, „Dobranoc” + nick). Przeciągnij w górę (albo kółko, dowolny
  klawisz, klik w napis na dole), a menu wysuwa się z animacją.
- Osobna tapeta dla ekranu blokady (domyślnie nowy **Żar**) i ekranu głównego (domyślnie **Magma**):
  6 animowanych tapet (Żar, Toń, Jedwab, Gwiezdny pył, Poświata, Wydmy), wszystkie niebiosa shadera
  oraz własne obrazy PNG / JPG / animowane GIF z `config/v1zir/wallpapers`.
- **Przytrzymaj** lewy przycisk myszy przez 3 s na pustym miejscu ekranu, żeby zmienić jego tapetę:
  karuzela kart z podglądem na żywo, rozmycie i przyciemnienie, dla obrazów Wypełnij / Dopasuj i ruch,
  „Dodaj obraz…”, folder, usuwanie oraz opcja „Na obu ekranach”.
- U góry ekranu głównego mały zegar z powitaniem.
- Okrągłe przyciski z ikonami: matowe, rozmyte wypełnienie, które po najechaniu rozjaśnia się od
  środka; sterowanie także klawiaturą (Tab / strzałki / Enter).
- Ustawienia w module **Main Menu**: ekran blokady, blokada po wyjściu ze świata, 12/24 h,
  sekundy, powitanie.

### Click GUI (Right Shift)
- Poziome okno w matowym stylu: u góry logo, nazwa, przycisk edytora HUD i wyszukiwarka
  (Ctrl+F albo po prostu pisz), po lewej zakładki **Visuals, Combat, Shaders, Hud, Settings**.
- Moduły jako karty z opisem i przełącznikiem; LPM włącza, PPM (lub strzałka) wysuwa panel
  ustawień z prawej, ŚPM przypisuje klawisz. Filtry podkategorii nad listą.
- **Settings**: motywy (domyślny V1zir – pomarańczowy gradient), edytor motywu, animacje
  otwierania (Normal, 3D) i zamykania (Fade out, **3D Frost** – okno pęka jak szkło),
  skala interfejsu, karty Main Menu i Custom UI oraz sekcja **Performance**.

### Visuals
View Model, Swing Animations, Hand Chams, Zoom (C), Free Look (Left Alt), Camera Tweaks
(kamera przez bloki, dystans w trzeciej osobie, bez trzęsienia), Aspect Ratio, No Render,
Jump Circles (tylko przy skoku), China Hat, Trails, Block Highlight, Break Effects, Item Physics.

### Combat
Target ESP, Hit Particles, Crosshair, Hit Color, Damage Numbers, Hit Marker, Kill Effects
(piorun, dusza, wybuch), Pop Chams, Totem Animation, Crystal Chams, Trajectories (łuk, kusza,
trójząb, perły, mikstury…), Damage Tint, Nametags, Entity Glow.

### Shaders
**Shader Sky** – niebo z presetami: Realistic (wolumetryczne chmury, atmosfera, księżyc
z kraterami), Magma, Aurora (kurtyny zorzy), Nebula, Sunset, Ink, Synthwave, Blood Moon, Custom.
Niebo jest liczone do panoram w tle i składane z nich, więc kosztuje mało i nie zależy od
rozdzielczości. Oprócz tego: **Block Reflection** (Normal – odbicia na wodzie i gładkich blokach,
Metal – wypolerowana, satynowa powierzchnia), **Rain Effects** (mokra ziemia w deszczu,
chlapnięcia przy skoku i lądowaniu), Color Grading, Ambience, Full Bright, World Particles,
Sky Lanterns, Glint Color.

### Hud
Watermark (z logo), Array List, Keybinds, Potions, Target HUD, Coordinates, Compass, Clock,
Speed, Server Info (ping, TPS), Performance (FPS i wykres czasu klatki), Keystrokes (z CPS),
Inventory, Armor, Item Counter, powiadomienia. Wszystko przesuwa się w edytorze HUD.

### Spotify
Bez konfiguracji: Windows 10/11 (systemowe sterowanie multimediami), macOS (aplikacja Spotify)
i Linux (`playerctl`). **Spotify** (HUD) – okładka, tytuł, wykonawca, pasek postępu, przyciski
działające przy otwartym czacie, style Card i Compact. **Spotify Lyrics** – zsynchronizowane
teksty z LRCLIB (zapisywane w `config/v1zir/lyrics`) w presetach **Window**, **3D** (błyszczące
napisy pojawiające się słowo po słowie przed graczem; styl Shine lub Extruded) i **Karaoke**.

### Optymalizacje (wbudowane, nie moduły)
Ukrywanie bytów i bloków z zawartością schowanych za ścianami, odcinanie z dystansu tekstu na
znakach, wzorów banerów, przedmiotów w ramkach i stojaków, limit nowych cząsteczek, 30 FPS gdy
okno jest w tle i brak renderowania po zminimalizowaniu, rozłożone wgrywanie chunków (bez
przycięć przy ładowaniu). Każdą można wyłączyć w Settings → Performance; przy Sodium, Entity
Culling, More Culling czy Dynamic FPS odpowiednie optymalizacje same się wyłączają.

Wszystkie funkcje są czysto wizualne.

## Budowanie

Wymagane: **JDK 21** i internet (Gradle pobiera Fabric Loom, mapowania Yarn i Minecrafta).

```
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

Gotowy plik: `build/libs/v1zir-visuals-1.0.0.jar` → wrzuć do folderu `mods` razem z
**Fabric API** dla 1.21.11. Test w środowisku deweloperskim: `./gradlew runClient`.

Automatyczny test klienta: `./gradlew runClientGameTest` uruchamia prawdziwego klienta, przechodzi
przez ekran blokady i menu główne, wszystkie presety nieba, odbicia i deszcz, HUD i Click GUI, a zrzuty ekranu zapisuje w
`build/run/clientGameTest/screenshots`. To samo robi GitHub Actions przy każdym PR (artefakty
`v1zir-visuals-jar` i `dev-client-screenshots`).

Konfiguracja zapisuje się w `config/v1zir/config.json`.

## Struktura

```
render/              renderer: pipeline'y, SDF kształty i tekst, rozmycie tła, sky
  font/              silnik fontów SDF (stb_truetype), atlasy, ikony Lucide
  sky/               Shader Sky + tło menu
  world/             efekty 3D (Target ESP, kółka, kapelusz, smugi, cząsteczki)
gui/menu/            menu główne i ekran blokady
gui/clickgui/        Click GUI, edytor motywu, edytor HUD
gui/hud/             renderowanie HUD i powiadomienia
module/              moduły i ustawienia
optimize/            wbudowane optymalizacje
mixin/               podpięcia do Minecrafta
assets/v1zir/shaders GLSL: sky_lib.glsl (wszystkie presety nieba), kształty SDF, tekst SDF, blur
```

Licencje dołączonych fontów: zobacz `LICENSE-ASSETS.md`.
