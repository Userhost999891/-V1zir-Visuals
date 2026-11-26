# Third-party notices

Aesthetic Visuals is original work, apart from the components below, which are used under their own licences.

## Bundled in the mod's jar

### MCEF Modern (Browser HUD)
- The Browser HUD runs on **MCEF Modern** (Minecraft Chromium Embedded Framework) by **DimasKama**, bundled
  unmodified inside the mod's jar (Fabric jar-in-jar), so nothing has to be installed separately.
- MCEF was originally created by **montoyo**, then rewritten by the **CinemaMod Group**, then rewritten again as MCEF
  Modern by DimasKama.
- Licence: GNU Lesser General Public License v2.1 (LGPL-2.1). The licence text ships in its jar
  (`LICENSE_mcef-modern`). Source: https://github.com/DimasKama/mcef-modern
- MCEF Modern uses **java-cef** (JCEF, BSD licence) and **jcefmaven** by FriwiDev (Apache License 2.0). On first use of
  the browser it downloads the Chromium Embedded Framework (CEF, BSD licence; Chromium, BSD-style licences) for your
  system into `config/mcef-modern`.

### JLayer
- MP3 decoding for the player's own UI sounds: **JLayer** 1.0.1 (JavaZOOM), LGPL-2.1, bundled unmodified.

## Assets

### Twemoji (emoji in Messages)
- Emoji graphics: **Twemoji**, Copyright 2019 Twitter, Inc and other contributors (now maintained at
  https://github.com/jdecked/twemoji). Licensed under **CC-BY 4.0**: https://creativecommons.org/licenses/by/4.0/
- Changes: the 32 px images were laid out in one atlas (`assets/v1zir/textures/font/emoji.png`), drawn in Messages.

### Emoji data
- Sequences, names and categories from **emoji-data** by Cal Henderson (https://github.com/iamcal/emoji-data), MIT
  licence.
- Shortcodes (Discord-style names such as `:sob:` and `:skull:`) from **emojibase-data** by Miles Johnson
  (https://github.com/milesj/emojibase), MIT licence.
