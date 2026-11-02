# Third-party notices

The code in this repository is released under the MIT License (see `LICENSE`).
It builds on, or was written against, the following work. None of the IR code
databases are stored in this repository; `pipeline/fetch_sources.sh` downloads them.

## IR code data (used by `pipeline/` to build `irdb.sqlite`)

| Source | License | Notes |
|---|---|---|
| [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) | CC0-1.0 | Remotes captured by the Flipper Zero community |
| [irdb](https://github.com/probonopd/irdb) by Simon Peter and contributors | [irdb license](https://github.com/probonopd/irdb/blob/master/LICENSE.md) | **Conditions apply when an app ships or accesses this data**: open an issue to inform the irdb project, include the notice below, and provide up to three copies of the product on request. Build without it with `python3 build_db.py --sources flipper,flipper_converted,irremoteesp8266`. |
| [IRremoteESP8266](https://github.com/crankyoldgit/IRremoteESP8266) | LGPL-2.1 | Compiled on the build machine by `pipeline/irremote/build_ac.sh` to generate A/C remote states; the library is not shipped in the app |

Required irdb notice, when the database includes irdb data:

> Contains/accesses irdb by Simon Peter and contributors, used under permission.
> For licensing details and for information on how to contribute to the database,
> see https://github.com/probonopd/irdb

## Fonts

| Font | License |
|---|---|
| [Be Vietnam Pro](https://github.com/bettergui/BeVietnamPro) (`android/app/src/main/res/font/`) | SIL Open Font License 1.1 — full text in `android/OFL-BeVietnamPro.txt` |

## Libraries (Gradle dependencies)

AndroidX / Jetpack Compose, Material Components, Kotlin coroutines, and
[OkHttp](https://github.com/square/okhttp) — Apache License 2.0.

## Protocol references

The network remotes are independent implementations; no code was copied.

- **Android TV Remote v2**: message definitions and pairing flow documented by
  [androidtvremote2](https://github.com/tronikos/androidtvremote2) (Apache-2.0),
  [androidtv-remote](https://github.com/louis49/androidtv-remote) (MIT) and the
  [protocol notes](https://github.com/Aymkdn/assistant-freebox-cloud/wiki/Google-TV-(aka-Android-TV)-Remote-Control-(v2))
  of assistant-freebox-cloud.
- **LG webOS (SSAP)**: the registration permission list follows
  [aiowebostv](https://github.com/home-assistant-libs/aiowebostv) (Apache-2.0).

## Trademarks

LG, webOS, Xiaomi, Mi Box, Android TV, Google TV, Panasonic and other product names
are trademarks of their respective owners. This project is not affiliated with or
endorsed by any of them.
