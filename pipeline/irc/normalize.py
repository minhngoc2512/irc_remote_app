"""Normalization tables: device categories, brand names and button functions."""
from __future__ import annotations

import re
from collections import Counter

# key, Vietnamese name, English name — order is the display order in the app.
CATEGORIES = [
    ("tv", "TV", "TV"),
    ("ac", "Điều hòa", "Air conditioner"),
    ("fan", "Quạt", "Fan"),
    ("stb", "Đầu thu / TV box", "Set-top box"),
    ("projector", "Máy chiếu", "Projector"),
    ("soundbar", "Loa / Soundbar", "Speaker / Soundbar"),
    ("audio", "Amply / Receiver", "Amplifier / Receiver"),
    ("player", "Đầu đĩa / Media", "Disc / Media player"),
    ("light", "Đèn", "Light"),
    ("air_purifier", "Lọc khí / Tạo ẩm", "Air purifier / Humidifier"),
    ("heater", "Máy sưởi", "Heater"),
    ("camera", "Camera", "Camera"),
    ("monitor", "Màn hình", "Monitor / Display"),
    ("switcher", "Bộ chuyển tín hiệu", "Switcher / Converter"),
    ("car", "Âm thanh ô tô", "Car audio"),
    ("vacuum", "Robot hút bụi", "Robot vacuum"),
    ("other", "Khác", "Other"),
]
CATEGORY_KEYS = {c[0] for c in CATEGORIES}

# Flipper-IRDB top-level folder -> category (None = skip the folder).
FLIPPER_CATEGORIES = {
    "TVs": "tv", "ACs": "ac", "Fans": "fan", "Projectors": "projector",
    "SoundBars": "soundbar", "Speakers": "soundbar", "Audio_and_Video_Receivers": "audio",
    "Blu-Ray": "player", "DVD_Players": "player", "CD_Players": "player", "VCR": "player",
    "Laserdisc": "player", "MiniDisc": "player", "Multimedia": "player",
    "Cable_Boxes": "stb", "Streaming_Devices": "stb", "DVB-T": "stb", "TV_Tuner": "stb",
    "LED_Lighting": "light", "Air_Purifiers": "air_purifier", "Humidifiers": "air_purifier",
    "Dust_Collectors": "air_purifier", "Heaters": "heater", "Fireplaces": "heater",
    "Cameras": "camera", "CCTV": "camera", "Monitors": "monitor", "Touchscreen_Displays": "monitor",
    "Digital_Signs": "monitor", "Whiteboards": "monitor", "Picture_Frames": "monitor",
    "Videoconferencing": "camera", "Converters": "switcher", "KVM": "switcher",
    "Car_Multimedia": "car", "Head_Units": "car", "Vacuum_Cleaners": "vacuum",
    "Window_cleaners": "vacuum", "Consoles": "other", "Computers": "other", "Clocks": "other",
    "Toys": "other", "Bidet": "other", "Handicap_Ceiling_Lifts": "other", "Miscellaneous": None,
    "Universal_TV_Remotes": None,  # power-code collections, not single devices
}

# IRDB device-type folder (lower-case) -> category, first match wins.
_IRDB_TYPE_RULES = [
    (r"air ?con|a/?c\b|climate", "ac"),
    (r"\bfan\b|ceiling", "fan"),
    (r"project|dlp|screen", "projector"),
    (r"satellite radio|radio", "audio"),
    (r"sat|cable|dss|dvr|tivo|set ?top|stb|hdtv tuner|digital tv|iptv|freeview|box", "stb"),
    (r"\btv\b|television|plasma|lcd|led|hdtv|oled", "tv"),
    (r"monitor|display", "monitor"),
    (r"sound ?bar|speaker|subwoofer", "soundbar"),
    (r"receiver|amp|surround|tuner|processor|pre-?amp|karaoke|mini ?system|music server|audio|hifi", "audio"),
    (r"dvd|blu|cd|vcr|laser|player|mp3|ipod|cassette|tape|dat\b|minidisc|jukebox|recorder|vhs|media", "player"),
    (r"light|lamp|dimmer", "light"),
    (r"switch|matrix|scaler|video processor|hdmi|kvm", "switcher"),
    (r"camera|camcorder", "camera"),
    (r"heater|fireplace", "heater"),
    (r"purifier|humidifier", "air_purifier"),
]


def irdb_category(type_folder: str) -> str | None:
    t = type_folder.lower()
    if t.startswith("unknown"):
        return None
    for pattern, cat in _IRDB_TYPE_RULES:
        if re.search(pattern, t):
            return cat
    return None


# --- Brands -------------------------------------------------------------------

_GENERIC_BRANDS = {"", "unknown", "unknownbrand", "generic", "misc", "miscellaneous", "noname", "other", "various"}
_BRAND_ALIASES = {
    "lgelectronics": "lg", "samsungelectronics": "samsung", "sonycorporation": "sony",
    "panasonicnational": "panasonic", "hisensevidaa": "hisense", "xiaomimi": "xiaomi", "mi": "xiaomi",
    "philipsmagnavox": "philips", "mitsubishielectric": "mitsubishi", "tcl": "tcl", "sharpaquos": "sharp",
}


def brand_norm(raw: str) -> str:
    n = re.sub(r"[^a-z0-9]", "", raw.lower())
    if n in _GENERIC_BRANDS:
        return "generic"
    return _BRAND_ALIASES.get(n, n)


class BrandNames:
    """Picks a display name per normalized brand: the most common spelling."""

    def __init__(self):
        self._seen: dict[str, Counter] = {}

    def add(self, raw: str) -> str:
        norm = brand_norm(raw)
        pretty = "Generic" if norm == "generic" else raw.replace("_", " ").strip()
        self._seen.setdefault(norm, Counter())[pretty] += 1
        return norm

    def display(self, norm: str) -> str:
        names = self._seen[norm].most_common()
        # Prefer a mixed/upper-case spelling over all-lowercase when equally common.
        best = max(names, key=lambda kv: (kv[1], not kv[0].islower(), -len(kv[0])))
        return best[0]


# --- Buttons --------------------------------------------------------------------

_DIGIT_WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
_UP = r"(?:up|plus|inc|increase)"
_DN = r"(?:down|dn|minus|dec|decrease)"

_BUTTON_RULES: list[tuple[str, str]] = [
    ("POWER", r"power|pwr|power toggle|on off|onoff|standby|stand by|power on off|power onoff|power standby|toggle power|turn on off"),
    ("POWER_ON", r"power on|pwr on|on|discrete on|turn on"),
    ("POWER_OFF", r"power off|pwr off|off|discrete off|turn off"),
    ("VOL_UP", rf"(?:vol|volume|v) ?{_UP}|vol up|louder"),
    ("VOL_DOWN", rf"(?:vol|volume|v) ?{_DN}|quieter"),
    ("MUTE", r"mute|muting|mute toggle|sound off"),
    ("CH_UP", rf"(?:ch|chan|channel|prog|program|pr|p|page) ?(?:{_UP}|next)"),
    ("CH_DOWN", rf"(?:ch|chan|channel|prog|program|pr|p|page) ?(?:{_DN}|prev)"),
    ("CH_PREV", r"recall|last|last ch|prev ch|pre ch|prech|previous channel|flashback|jump|q view|quick view"),
    *[(f"DIGIT_{i}", rf"{i}|digit {i}|num {i}|number {i}|n{i}|{w}") for i, w in enumerate(_DIGIT_WORDS)],
    ("UP", r"up|up arrow|cursor up|arrow up|nav up|menu up|dpad up|navi up"),
    ("DOWN", r"down|dn|down arrow|dn arrow|cursor down|arrow down|nav down|menu down|dpad down|navi down"),
    ("LEFT", r"left|left arrow|cursor left|arrow left|nav left|menu left|dpad left|navi left"),
    ("RIGHT", r"right|right arrow|cursor right|arrow right|nav right|menu right|dpad right|navi right"),
    ("OK", r"ok|enter|select|confirm|center|cursor enter|ok enter|enter ok"),
    ("BACK", r"back|return|ret|go back"),
    ("EXIT", r"exit|quit|cancel"),
    ("MENU", r"menu|setup menu|main menu|menu toggle"),
    ("HOME", r"home|smart hub|smarthub|smart|homepage"),
    ("INFO", r"info|display|information|disp|osd|status"),
    ("GUIDE", r"guide|epg|tv guide|program guide"),
    ("SETTINGS", r"settings|setup|tools|options|option|setting"),
    ("INPUT", r"input|source|src|av|tv av|av tv|tv video|input select|inputs|tv input|input source|ext|external|video"),
    ("PLAY_PAUSE", r"play pause|playpause|pause play|play stop"),
    ("PLAY", r"play"),
    ("PAUSE", r"pause|still"),
    ("STOP", r"stop"),
    ("REWIND", r"rew|r srch|r search|rewind|fast rewind|rwd|fr|fast reverse|reverse|scan rev|search rev"),
    ("FAST_FORWARD", r"ff|f srch|f search|fastforward|fwd|fast forward|forward|ffwd|fast fwd|scan fwd|search fwd"),
    ("PREV", r"prev|previous|skip back|skip prev|skip minus|track prev|chapter prev|skip down|skip rev|rev skip|prev track|prev chapter|skip previous"),
    ("NEXT", r"next|skip fwd|skip next|skip forward|skip plus|track next|skip up|next track|next chapter|fwd skip"),
    ("RECORD", r"rec|record"),
    ("EJECT", r"eject|open close|open|disc open|open eject"),
    ("RED", r"red|r|key red|a red"),
    ("GREEN", r"green|g|key green|b green"),
    ("YELLOW", r"yellow|y|key yellow|c yellow"),
    ("BLUE", r"blue|b|key blue|d blue"),
    ("SUBTITLE", r"subtitle|subtitles|sub|subt|cc|caption|closed caption"),
    ("AUDIO_TRACK", r"audio|lang|language|mts|sap|audio track"),
    ("SLEEP", r"sleep|sleep timer"),
    ("TEMP_UP", rf"(?:temp|temperature|tmp|t|heat|warmer) ?{_UP}|warmer"),
    ("TEMP_DOWN", rf"(?:temp|temperature|tmp|t) ?{_DN}|cooler"),
    ("MODE", r"mode|ac mode|mode select|function"),
    ("FAN_SPEED", r"fan|fan speed|speed|wind|wind speed|fan mode"),
    ("SPEED_UP", rf"(?:speed|fan|fan speed|wind) ?{_UP}|faster"),
    ("SPEED_DOWN", rf"(?:speed|fan|fan speed|wind) ?{_DN}|slower"),
    ("SWING", r"swing|oscillate|osc|oscillation|rotate|rotation|swing vertical|swing horizontal|swing on off|louver|vane"),
    ("TIMER", r"timer|time|timer on off|timer set"),
    ("BRIGHT_UP", rf"(?:bright|brightness|light|dim) ?{_UP}|brighter"),
    ("BRIGHT_DOWN", rf"(?:bright|brightness|light) ?{_DN}|dim|dimmer|darker"),
    ("ZOOM", r"zoom|aspect|wide|ratio|picture size|p size"),
]
_BUTTON_RES = [(k, re.compile(rf"^(?:{p})$")) for k, p in _BUTTON_RULES]
_PREFIX = re.compile(r"^(?:key|btn|button|kp|ir|cmd|remote|tv|rc) (?=\S)")


def button_tokens(label: str) -> str:
    t = label.lower()
    t = re.sub(r"(?<=[a-z])(?=[0-9])|(?<=[0-9])(?=[a-z])", " ", t)
    t = re.sub(r"\+", " plus ", t)
    t = re.sub(r"(?:(?<=\s)|(?<=[a-z]))-(?=\s|$)", " minus ", t)
    t = re.sub(r"[^a-z0-9]+", " ", t).strip()
    t = re.sub(r"\s+", " ", t)
    prev = None
    while prev != t:
        prev, t = t, _PREFIX.sub("", t)
    return t


def button_key(label: str) -> str | None:
    t = button_tokens(label)
    for key, rx in _BUTTON_RES:
        if rx.match(t):
            return key
    return None


# --- Category inference -----------------------------------------------------------

_AC_WORDS = re.compile(r"\b(cool|heat|dry|dehumid|auto|temp|celsius|fahrenheit|1[6-9]|2[0-9]|30)\b")
_PROJ_WORDS = re.compile(r"\b(keystone|lamp|focus|freeze|blank|projector|hdmi 2)\b")


def infer_category(keys: set[str], labels: list[str]) -> str:
    """Guess a device category from the buttons of an uncategorized codeset."""
    joined = " ".join(button_tokens(l) for l in labels)
    has = keys.__contains__
    if has("TEMP_UP") or has("TEMP_DOWN") or (len(_AC_WORDS.findall(joined)) >= 3 and not has("CH_UP")):
        return "ac"
    if _PROJ_WORDS.search(joined) and not has("CH_UP"):
        return "projector"
    if (has("BRIGHT_UP") or has("BRIGHT_DOWN")) and not has("VOL_UP"):
        return "light"
    if (has("SWING") or has("SPEED_UP") or has("FAN_SPEED")) and not has("VOL_UP") and not has("CH_UP"):
        return "fan"
    digits = sum(has(f"DIGIT_{i}") for i in range(10))
    if has("CH_UP") and has("VOL_UP") and (has("INPUT") or has("ZOOM") or has("SLEEP")):
        return "tv"
    if has("CH_UP") and (has("GUIDE") or has("RECORD")):
        return "stb"
    if has("CH_UP") and has("VOL_UP") and digits >= 8:
        return "tv"
    if (has("PLAY") or has("STOP") or has("PLAY_PAUSE")) and (has("EJECT") or has("REWIND") or has("FAST_FORWARD") or has("NEXT")):
        return "player"
    if has("VOL_UP") and has("INPUT") and not has("CH_UP"):
        return "audio"
    if has("VOL_UP") and has("MUTE") and not has("CH_UP"):
        return "soundbar"
    return "other"
