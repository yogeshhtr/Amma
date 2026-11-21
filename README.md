# AMMA (आमा)

A very simple Nepali video app for Amma (Grandma), for Android phones and Android TV.
**No words to read** – only pictures and icons.

## What she sees
* **Opening picture** – green, golden ॐ, lotus and two diyas (2 s; tap to skip).
* **Top row of round icons** (always there, the current one has a gold ring):
  🏠 home · 📺 serials · 🎬 movies · 📖 Herne Katha · 🙏 god / bhajan · 🕉 pravachan · 📡 live TV (only after an
  m3u link was added – always after the YouTube icons) · 🆕/🔀 order of the home page (home only)
* **🏠 Home (opens first)** – the newest videos of everything mixed: serial episodes, movies, Herne Katha, bhajan,
  pravachan. The newest of every program first, not-yet-watched first. Each picture has a small corner icon
  (📺 🎬 🙏 🕉 …) showing what it is.
  * The big wide tile with **▶** on top = continue the last video she was watching.
  * **🆕** = newest first; tap it → **🔀** = shuffled (remembered). When everything on top was already watched,
    the home page is shuffled by itself.
  * After a video from the home page ends, the next tile of the home page plays.
* **A program icon** → its programs (each shows the picture of its newest episode) → the episodes.
  The newest episode has a **⭐**.
* On every picture: **green ✔** = already watched, **red bar** = how far she watched.
* **Player** – tap the picture: ⏮ ⏪ ⏸/▶ ⏩ ⏭ and ✕ (back). No words. A video continues where she stopped; when
  it ends the next one starts (serials: the next newer episode). A video that cannot play is skipped (⚠ ⏭).
* **No internet / nothing found**: 📶✖ 🔄 – tap it to try again.
* **TV remote** – arrows move a gold frame, OK opens. Player: OK = pause/play, ◀ ▶ = back 10 s / forward 30 s,
  CH+ / CH- = next / previous video, Back = close. Live TV: ▲ ▼ = other channel.

## Programs
* 📺 Serials: भुन्टी (@sukumaya98tv), बुहारी (channel of the linked episode), सकस (@MasterTVNepal), कान्छी
  (@kanchhiofficial8023), and by YouTube search (newest uploads, only titles with the serial's name): मेरी बास्सै,
  जुठे, जीवनको रङ केशरी, हल्का रमाइलो, सक्किगोनी, भद्रगोल, जिरे खुर्सानी
* 🎬 Nepali full movies (> 1 hour), old Nepali movies, OSR movies
* 📖 Herne Katha · 🙏 the devotional playlist, Nepali bhajan, Hindi bhajan · 🕉 GuruKul Production pravachan
* New episodes come by themselves (lists older than 3 hours are downloaded again in the background).

## For the family (helper menu)
Long-press the **logo** (top left) or press the **Menu** key on a remote:
* ↻ download everything again
* ＋ add a YouTube channel link / @name, playlist link, video link (= that channel's videos) or search words to a tile
* 📡 add live TV: an **m3u** link. Its channels appear under 📡, after the YouTube icons (copy-protected channels
  are skipped; a channel that does not start in 12 s is skipped)
* 👁 show / hide programs · 🗑 remove an added program

Built-in programs: `app/src/main/java/com/amma/tv/Catalog.kt`.

## Pictures
* Opening picture: `app/src/main/res/drawable-nodpi/splash_god.jpg` – replace it with any picture (same name, .jpg).
* App icon: `mipmap-*/ic_launcher*.png`, `drawable-xxxhdpi/ic_launcher_fg.png`. TV banner: `drawable-xhdpi/tv_banner.png`.

## Building
Put this folder in its own GitHub repository → Actions builds **AMMA.apk** (Release `vNN`).
Separate app (`com.amma.tv`): installs next to Yogi TV. Signed with `amma.p12` (copy of the Yogi TV key), so a new
AMMA.apk installs over the old one and keeps what she watched.
