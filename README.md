# EzRemote

EzRemote is an Android app that turns your phone into a remote for the devices at home:

- **Infrared (IR)** on phones with an IR blaster (Xiaomi, Redmi, POCO…): TVs, air conditioners,
  fans, lights, speakers, set-top boxes, projectors… with 7,000+ codesets merged from open
  databases.
- **Over Wi-Fi**, on any phone, IR blaster or not:
  - **Android TV / Google TV** (Mi Box, Android-based TVs) via the Android TV Remote v2 protocol:
    pair with the code shown on the TV; D-pad or swipe pad, app shortcuts, text input, voice.
  - **LG webOS TVs** via SSAP: pair by accepting on the TV; a real mouse cursor, the TV's own app
    list, HDMI input picker, text input, and power-on with Wake-on-LAN.

Landing page: <https://minhngoc2512.github.io/irc_remote_app/>

```
pipeline/   Python + C++: fetch sources, decode protocols, normalize, dedupe -> out/irdb.sqlite
android/    Kotlin + Jetpack Compose app
  i18n/     UI strings for every language (generates res/values*/strings.xml)
  ir/       IR transmit through ConsumerIrManager
  atv/      Android TV Remote v2 (protobuf, TLS, pairing, mDNS)
  webos/    LG webOS (SSAP WebSocket, pointer socket, SSDP + port sweep, Wake-on-LAN)
docs/       landing page (GitHub Pages)
```

## Install

Download the APK from [Releases](https://github.com/minhngoc2512/irc_remote_app/releases).
Android 8.0+, in English, Tiếng Việt, 日本語, 한국어, हिन्दी, ไทย, ລາວ and Bahasa Indonesia
(follows the phone's language; change it from the globe icon on the home screen). IR remotes need a phone with an IR blaster; Wi-Fi remotes need the phone and the TV
on the same network.

## Build the IR code database

The repository does not contain IR code data; the pipeline downloads the sources and builds
`pipeline/out/irdb.sqlite`. Requires Python 3.10+ and g++ (for the A/C part only).

```bash
cd pipeline
./fetch_sources.sh          # clone Flipper-IRDB, irdb and IRremoteESP8266 into pipeline/sources/
./irremote/build_ac.sh      # build IRremoteESP8266 for the host and generate A/C states
python3 build_db.py         # -> out/irdb.sqlite
```

What the pipeline does:

1. **Decode**: every protocol-based signal (NEC, RC5/RC6, Sony, Samsung, Kaseikyo, JVC, Denon,
   Sharp, RCA, DirecTV, Dish…, see `irc/encoders.py`) is rendered to microsecond timings. The app
   just transmits them; it needs no encoders.
2. **Air conditioners**: `irremote/gen_ac.cpp` calls IRremoteESP8266's `IRac::sendAc()` for each
   protocol and remote model: off, 4 modes × 16–30 °C, fan speeds, swing. Every state carries
   `params` so the app can draw a thermostat-style A/C panel.
3. **Categorize**: the device type comes from the source's folders; codesets without one are
   inferred from their button names (`irc/normalize.py`). Brands and button names are normalized
   (`POWER`, `VOL_UP`, `CH_UP`, `DIGIT_1`…).
4. **Dedupe**: a codeset whose signals are all contained in a larger codeset of the same brand is
   merged into it.

Schema: `pipeline/schema.sql`.

## Build the app

Requires JDK 17 and Android SDK 35.

```bash
cd android
./gradlew assembleDebug     # packages ../pipeline/out/irdb.sqlite as an asset
```

Release builds are signed with a key kept outside the repository; point
`android/keystore.properties` (git-ignored) at it:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=irremote
keyPassword=...
```

```bash
./gradlew assembleRelease
```

- **IR**: pick a device type → brand → try codesets one by one → save the one that works → a
  remote laid out for that device type (D-pad, volume/channel, number pad, media, A/C panel…).
- **Android TV / LG**: Add device → the "over Wi-Fi" tiles at the top → pick a discovered TV (or
  type its IP) → pair.

### Probing real TVs

JVM tests can talk to a real TV on your network (skipped unless the variables are set):

```bash
cd android
WEBOS_HOST=192.168.1.20 WEBOS_STORE=/tmp/webos.properties ./gradlew testDebugUnitTest --tests '*WebOsProbeTest*'
ATV_HOST=192.168.1.30 ./gradlew testDebugUnitTest --tests '*AtvProtocolTest*'
```

## License

Code: [MIT](LICENSE). The IR code sources, the font and the libraries have their own licenses, see
[NOTICE.md](NOTICE.md).

> **Shipping an APK that includes irdb data**: open an issue to inform the irdb project, keep the
> attribution (shown on the app's Data sources screen) and provide copies of the app on request.
> Build without irdb with `python3 build_db.py --sources flipper,flipper_converted,irremoteesp8266`.

This project is not affiliated with or endorsed by LG, Xiaomi, Google or any device maker.
