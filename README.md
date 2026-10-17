# V1zir Visuals

Wizualny mod kliencki dla **Minecraft 1.21.11 (Fabric)**: shaderowe niebo z presetami, własne
animowane menu główne, Click GUI w stylu CatLean / Nursultan, HUD i efekty PvP. Cały interfejs
jest rysowany przez własny renderer: zaokrąglone kształty liczone jako SDF (idealnie gładkie
krawędzie w każdej skali GUI), rozmyte "szklane" panele i własne fonty renderowane jako
**signed distance field** – ostre przy każdym rozmiarze.

## Funkcje

### Shader Sky (Visuals)
Niebo zastąpione pełnoekranowym shaderem GLSL. Presety:

| Preset | Opis |
|---|---|
| **Realistic** | fizyczne rozpraszanie Rayleigh + Mie z warstwą ozonu, słońce, księżyc z fazami i kraterami, gwiazdy, Droga Mleczna, chmury z samocieniowaniem; reaguje na porę dnia i deszcz |
| **Magma** | biała, realistyczna lawa na czarnym niebie – płyty zastygłej skorupy, żarzące się pęknięcia, jeziora lawy z wyspami skorupy, iskry |
| **Aurora** | zorza polarna (kurtyny z promieniami) nad gwiaździstym niebem |
| **Nebula** | wolumetryczna mgławica w głębokim kosmosie |
| **Sunset** | wieczna złota godzina z różowymi chmurami |
| **Ink** | czarno-biały dym / atrament |
| **Synthwave** | retro słońce z paskami, neonowa siatka |
| **Blood Moon** | krwawy księżyc i czerwone chmury |
| **Custom** | własne kolory zenitu i horyzontu |

Ustawienia: jasność, prędkość animacji, jakość (Low/Medium/High), chmury i ich pokrycie,
gęstość gwiazd, własne kolory, waniliowe słońce i księżyc, synchronizacja mgły z horyzontem,
zastąpienie nieba w Endzie.

### Menu główne
Stare menu jest całkowicie zastąpione. Tło to animowany shader (dowolny preset nieba,
domyślnie Magma) z paralaksą myszy, unoszącymi się cząsteczkami, winietą i ziarnem. Napis
**V1zir Visuals** jest rysowany własnym fontem (Montserrat ExtraBold, SDF) z poświatą.
Zamiast zwykłych przycisków są **okrągłe przyciski z ikonami** – bez tooltipów i bez animacji,
po najechaniu po prostu się podświetlają (Singleplayer, Multiplayer, Realms, Mod Menu jeśli jest
zainstalowane, Opcje, Wyjście, Język).

### Click GUI (Right Shift)
- panele kategorii na rozmytym "szkle", przeciągane, z przewijaniem,
- pasek zakładek **Modules / Hud / Themes** i wyszukiwarka (wystarczy zacząć pisać),
- LPM – włącz/wyłącz, PPM – ustawienia rozwijane w miejscu, ŚPM – przypisanie klawisza,
- ustawienia: przełączniki, suwaki z gradientem, tryby jako "chipy", próbnik koloru HSV z tęczą,
- **Theme Editor**: presety V1zir, Midnight, Neverlose, Fatality, Skeet, Primo, Femboy, Mono,
  Catlean Gray i Custom, przezroczystość paneli, rozmycie, wybór fontu
  (Inter, Montserrat, Rubik, Nunito, Comfortaa),
- zakładka **Hud**: przeciąganie elementów HUD myszką.

### HUD
Watermark (logo, nick, serwer, FPS, ping, godzina), Array List, Keybinds, Potions, Target HUD
(głowa, pasek zdrowia z animacją obrażeń), Coordinates, powiadomienia o przełączeniu modułów.

### Efekty
Target ESP (Ghosts / Ring / Marker), Jump Circles, China Hat, Trails, Hit Particles,
World Particles (świetliki, śnieg, gwiazdy, żar), własny celownik, Ambience (czas i pogoda
po stronie klienta), Full Bright, View Model.

Wszystkie efekty są czysto wizualne i testowane głębią względem świata – nic nie jest widoczne
przez ściany.

## Budowanie

Wymagane: **JDK 21** i internet (Gradle pobiera Fabric Loom, mapowania Yarn i Minecrafta).

```
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

Gotowy plik: `build/libs/v1zir-visuals-1.0.0.jar` → wrzuć do folderu `mods` razem z
**Fabric API** dla 1.21.11. Test w środowisku deweloperskim: `./gradlew runClient`.

Konfiguracja zapisuje się w `config/v1zir/config.json`.

## Struktura

```
render/              renderer: pipeline'y, SDF kształty i tekst, rozmycie tła, sky
  font/              silnik fontów SDF (stb_truetype), atlasy, ikony Lucide
  sky/               Shader Sky + tło menu
  world/             efekty 3D (Target ESP, kółka, kapelusz, smugi, cząsteczki)
gui/menu/            nowe menu główne
gui/clickgui/        Click GUI, edytor motywu, edytor HUD
gui/hud/             renderowanie HUD i powiadomienia
module/              moduły i ustawienia
mixin/               podpięcia do Minecrafta
assets/v1zir/shaders GLSL: sky_lib.glsl (wszystkie presety nieba), kształty SDF, tekst SDF, blur
```

Licencje dołączonych fontów: zobacz `LICENSE-ASSETS.md`.
