"""IR protocol encoders: (protocol, params) -> carrier frequency + timings.

Each encoder returns a Signal with an intro sequence (sent once) and a repeat
sequence (sent while a button is held). Durations are microseconds, alternating
mark/space and always starting with a mark. Definitions follow the IRP notation
used by IrpProtocols / DecodeIR; comments quote the IRP each encoder implements.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Signal:
    freq: int
    intro: list[int]
    repeat: list[int]
    # Extra copies of the repeat frame needed for a single short press
    # (e.g. Sony devices ignore commands seen fewer than three times).
    min_repeats: int = 0

    def press(self) -> list[int]:
        """Timings for one short button press."""
        frames = list(self.intro)
        n = self.min_repeats if self.intro else max(1, self.min_repeats)
        for _ in range(n):
            frames += self.repeat
        return frames


class UnsupportedProtocol(Exception):
    pass


@dataclass
class _Frame:
    """Accumulates signed durations (positive = mark, negative = space)."""
    unit: float
    zero: tuple
    one: tuple
    msb: bool = False
    out: list = field(default_factory=list)

    def d(self, *units: float) -> "_Frame":
        for u in units:
            self.out.append(u * self.unit)
        return self

    def us(self, *durations: float) -> "_Frame":
        self.out.extend(durations)
        return self

    def bits(self, value: int, width: int, msb: bool | None = None, spec: tuple | None = None) -> "_Frame":
        msb = self.msb if msb is None else msb
        order = range(width - 1, -1, -1) if msb else range(width)
        for i in order:
            sym = (spec or (self.zero, self.one))[(value >> i) & 1]
            self.d(*sym)
        return self

    def symbols(self, value: int, count: int, spec: tuple, bits_per_symbol: int, msb: bool = True) -> "_Frame":
        """Multi-level symbols (e.g. DirecTV / Nokia send 2 bits per symbol)."""
        mask = (1 << bits_per_symbol) - 1
        idx = range(count - 1, -1, -1) if msb else range(count)
        for i in idx:
            self.d(*spec[(value >> (i * bits_per_symbol)) & mask])
        return self

    def extent(self, total_us: float) -> "_Frame":
        """IRP '^': pad with silence so the frame lasts total_us in total."""
        used = sum(abs(x) for x in self.out)
        self.out.append(-max(total_us - used, 1000))
        return self

    def timings(self) -> list[int]:
        merged: list[float] = []
        for x in self.out:
            if merged and (merged[-1] > 0) == (x > 0):
                merged[-1] += x
            else:
                merged.append(x)
        while merged and merged[0] < 0:
            merged.pop(0)
        return [max(1, int(round(abs(x)))) for x in merged]


def _inv(v: int, width: int) -> int:
    return ~v & ((1 << width) - 1)


def _p(params: dict, key: str, default: int | None = None) -> int:
    v = params.get(key)
    if v is None or v < 0:
        if default is None:
            raise ValueError(f"missing parameter {key}")
        return default
    return v


# --- NEC family -------------------------------------------------------------

def _nec_frame(lead: int, D: int, S: int, data_tail) -> list[int]:
    f = _Frame(564, (1, -1), (1, -3)).d(lead, -8).bits(D, 8).bits(S, 8)
    data_tail(f)
    return f.d(1).extent(108_000).timings()


def _nec_ditto() -> list[int]:
    return _Frame(564, (1, -1), (1, -3)).d(16, -4, 1).extent(108_000).timings()


def nec(variant: str, p: dict) -> Signal:
    D = _p(p, "D")
    S = _p(p, "S", _inv(D, 8) if not variant.startswith("NECx") else D)
    F = _p(p, "F")

    def tail(f: _Frame):
        if variant.endswith("-f16"):
            f.bits(F, 16)
        elif variant.endswith("-y1"):  # 8th bit of ~F not inverted
            f.bits(F, 8).bits(_inv(F, 8), 7).bits(F >> 7, 1)
        elif variant.endswith("-y2"):  # 7th bit of ~F not inverted
            f.bits(F, 8).bits(_inv(F, 8), 6).bits(F >> 6, 1).bits(_inv(F >> 7, 1), 1)
        elif variant.endswith("-y3"):  # 7th and 8th bits not inverted
            f.bits(F, 8).bits(_inv(F, 8), 6).bits(F >> 6, 2)
        else:
            f.bits(F, 8).bits(_inv(F, 8), 8)

    base = variant.split("-")[0]
    if base == "NEC1":
        # {38.4k,564}<1,-1|1,-3>(16,-8,D:8,S:8,F:8,~F:8,1,^108m,(16,-4,1,^108m)*)
        return Signal(38400, _nec_frame(16, D, S, tail), _nec_ditto())
    if base == "NEC2":
        # {38.4k,564}<1,-1|1,-3>(16,-8,D:8,S:8,F:8,~F:8,1,^108m)+
        frame = _nec_frame(16, D, S, tail)
        return Signal(38400, frame, frame)
    if base == "NECx1":
        # {38.4k,564}<1,-1|1,-3>(8,-8,D:8,S:8,F:8,~F:8,1,^108m,(8,-8,D:1,1,^108m)*)
        rep = _Frame(564, (1, -1), (1, -3)).d(8, -8).bits(D, 1).d(1).extent(108_000).timings()
        return Signal(38400, _nec_frame(8, D, S, tail), rep)
    if base == "NECx2":
        # {38.4k,564}<1,-1|1,-3>(8,-8,D:8,S:8,F:8,~F:8,1,^108m)+
        frame = _nec_frame(8, D, S, tail)
        return Signal(38400, frame, frame)
    raise UnsupportedProtocol(variant)


def nec42(p: dict) -> Signal:
    # Aiwa / NEC42: {38k,550}<1,-1|1,-3>(16,-8,D:8,S:5,~D:8,~S:5,F:8,~F:8,1,-42,(16,-8,1,-165)*)
    A = _p(p, "D") | (_p(p, "S", 0) << 8)
    F = _p(p, "F")
    f = _Frame(550, (1, -1), (1, -3)).d(16, -8).bits(A, 13).bits(_inv(A, 13), 13).bits(F, 8).bits(_inv(F, 8), 8).d(1, -42)
    rep = _Frame(550, (1, -1), (1, -3)).d(16, -8, 1, -165).timings()
    return Signal(38000, f.timings(), rep)


def pioneer(p: dict) -> Signal:
    # {40k,564}<1,-1|1,-3>(16,-8,D:8,S:8,F:8,~F:8,1,^108m)+
    D = _p(p, "D")
    frame = _nec_frame(16, D, _p(p, "S", _inv(D, 8)), lambda f: f.bits(_p(p, "F"), 8).bits(_inv(_p(p, "F"), 8), 8))
    return Signal(40000, frame, frame)


def proton(p: dict) -> Signal:
    # {38k,500}<1,-1|1,-3>(16,-8,D:8,1,-8,F:8,1,^63m)+
    f = _Frame(500, (1, -1), (1, -3)).d(16, -8).bits(_p(p, "D"), 8).d(1, -8).bits(_p(p, "F"), 8).d(1).extent(63_000)
    t = f.timings()
    return Signal(38000, t, t)


def jvc(p: dict, frames: int = 1) -> Signal:
    # {38k,525}<1,-1|1,-3>(16,-8,(D:8,F:8,1,-45)+)
    body = _Frame(525, (1, -1), (1, -3)).bits(_p(p, "D"), 8).bits(_p(p, "F"), 8).d(1, -45).timings()
    intro = _Frame(525, (1, -1), (1, -3)).d(16, -8).timings() + body
    return Signal(38000, intro, body, min_repeats=frames - 1)


def mitsubishi(p: dict) -> Signal:
    # {32.6k,300}<1,-3|1,-7>(D:8,F:8,1,-80)+
    t = _Frame(300, (1, -3), (1, -7)).bits(_p(p, "D"), 8).bits(_p(p, "F"), 8).d(1, -80).timings()
    return Signal(32600, t, t)


def akai(p: dict) -> Signal:
    # {38k,289}<1,-2.6|1,-6.3>(D:3,F:7,1,^25.3m)+
    t = _Frame(289, (1, -2.6), (1, -6.3)).bits(_p(p, "D"), 3).bits(_p(p, "F"), 7).d(1).extent(25_300).timings()
    return Signal(38000, t, t)


def sharp_like(p: dict, freq: int, c1: int, c2: int) -> Signal:
    # Sharp: {38k,264}<1,-3|1,-7>(D:5,F:8,1:2,1,-165,D:5,~F:8,2:2,1,-165)+
    # Denon: same with 0:2 / 3:2 trailers
    D, F = _p(p, "D"), _p(p, "F")
    t = (_Frame(264, (1, -3), (1, -7)).bits(D, 5).bits(F, 8).bits(c1, 2).d(1, -165)
         .bits(D, 5).bits(_inv(F, 8), 8).bits(c2, 2).d(1, -165).timings())
    return Signal(freq, t, t)


def panasonic_old(p: dict) -> Signal:
    # {57.6k,833}<1,-1|1,-3>(4,-4,D:5,F:6,~D:5,~F:6,1,-44m)+
    D, F = _p(p, "D"), _p(p, "F")
    t = (_Frame(833, (1, -1), (1, -3)).d(4, -4).bits(D, 5).bits(F, 6).bits(_inv(D, 5), 5)
         .bits(_inv(F, 6), 6).d(1).us(-44_000).timings())
    return Signal(57600, t, t)


def rca(p: dict, freq: int = 58000) -> Signal:
    # {58k,460,msb}<1,-2|1,-4>(8,-8,D:4,F:8,~D:4,~F:8,1,-16)+
    D, F = _p(p, "D"), _p(p, "F")
    t = (_Frame(460, (1, -2), (1, -4), msb=True).d(8, -8).bits(D, 4).bits(F, 8).bits(_inv(D, 4), 4)
         .bits(_inv(F, 8), 8).d(1, -16).timings())
    return Signal(freq, t, t)


def recs80(p: dict) -> Signal:
    # {38k,158,msb}<1,-31|1,-47>(1:1,T:1,D:3,F:6,1,-45m)+
    t = (_Frame(158, (1, -31), (1, -47), msb=True).bits(1, 1).bits(_p(p, "T", 0), 1).bits(_p(p, "D"), 3)
         .bits(_p(p, "F"), 6).d(1).us(-45_000).timings())
    return Signal(38000, t, t)


# --- Sony ------------------------------------------------------------------

def sony(bits: int, p: dict) -> Signal:
    # Sony12: {40k,600}<1,-1|2,-1>(4,-1,F:7,D:5,^45m)+   Sony15: D:8   Sony20: D:5,S:8
    f = _Frame(600, (1, -1), (2, -1)).d(4, -1).bits(_p(p, "F"), 7)
    if bits == 12:
        f.bits(_p(p, "D"), 5)
    elif bits == 15:
        f.bits(_p(p, "D"), 8)
    else:
        f.bits(_p(p, "D"), 5).bits(_p(p, "S", 0), 8)
    t = f.extent(45_000).timings()
    return Signal(40000, t, t, min_repeats=2)


# --- Kaseikyo family ---------------------------------------------------------

def _kaseikyo_bytes(data: list[int]) -> list[int]:
    # {37k,432}<1,-1|1,-3>(8,-4, bytes LSB first ..., 1,-173)+
    f = _Frame(432, (1, -1), (1, -3)).d(8, -4)
    for b in data:
        f.bits(b, 8)
    return f.d(1, -173).timings()


def panasonic(p: dict) -> Signal:
    # Panasonic: (8,-4,2:8,32:8,D:8,S:8,F:8,(D^S^F):8,1,-173)+
    D, S, F = _p(p, "D"), _p(p, "S", 0), _p(p, "F")
    t = _kaseikyo_bytes([2, 32, D, S, F, D ^ S ^ F])
    return Signal(37000, t, t)


def denon_k(p: dict) -> Signal:
    # (8,-4,84:8,50:8,0:4,D:4,S:4,F:12,((D*16)^S^(F*16)^(F:8:4)):8,1,-173)+
    D, S, F = _p(p, "D"), _p(p, "S", 0), _p(p, "F")
    bits = 0 | (D << 4) | (S << 8) | (F << 12)  # 0:4,D:4,S:4,F:12 -> 24 bits LSB first
    chk = ((D * 16) ^ S ^ (F * 16) ^ ((F >> 4) & 0xFF)) & 0xFF
    t = _kaseikyo_bytes([84, 50, bits & 0xFF, (bits >> 8) & 0xFF, (bits >> 16) & 0xFF, chk])
    return Signal(37000, t, t)


def fujitsu(p: dict) -> Signal:
    # {37k,432}<1,-1|1,-3>(8,-4,20:8,99:8,0:4,E:4,D:8,S:8,F:8,1,-110)+
    E = _p(p, "E", 0)
    f = _Frame(432, (1, -1), (1, -3)).d(8, -4).bits(20, 8).bits(99, 8).bits(0, 4).bits(E, 4)
    t = f.bits(_p(p, "D"), 8).bits(_p(p, "S", 0), 8).bits(_p(p, "F"), 8).d(1, -110).timings()
    return Signal(37000, t, t)


def flipper_kaseikyo(address: int, command: int) -> Signal:
    """Flipper's Kaseikyo layout (firmware infrared_encoder_kaseikyo.c)."""
    vid = (address >> 8) & 0xFFFF
    g1, g2, ident = (address >> 4) & 0xF, address & 0xF, (address >> 24) & 3
    d0, d1 = vid & 0xFF, vid >> 8
    par = d0 ^ d1
    d2 = ((par & 0xF) ^ (par >> 4)) | (g1 << 4)
    d3 = g2 | ((command & 0xF) << 4)
    d4 = ((ident << 6) | (command >> 4)) & 0xFF
    t = _kaseikyo_bytes([d0, d1, d2 & 0xFF, d3 & 0xFF, d4, (d2 ^ d3 ^ d4) & 0xFF])
    return Signal(37000, t, t)


# --- Bi-phase ----------------------------------------------------------------

def rc5(p: dict) -> Signal:
    # {36k,msb,889}<1,-1|-1,1>(1,~F:1:6,T:1,D:5,F:6,^114m)*
    F = _p(p, "F")
    f = (_Frame(889, (1, -1), (-1, 1), msb=True).d(1).bits(_inv(F >> 6, 1), 1).bits(_p(p, "T", 0), 1)
         .bits(_p(p, "D"), 5).bits(F, 6).extent(114_000))
    return Signal(36000, [], f.timings())


def rc6(p: dict) -> Signal:
    # {36k,444,msb}<-1,1|1,-1>(6,-2,1:1,0:3,<-2,2|2,-2>(T:1),D:8,F:8,^107m)*
    f = _Frame(444, (-1, 1), (1, -1), msb=True).d(6, -2).bits(1, 1).bits(0, 3)
    f.bits(_p(p, "T", 0), 1, spec=((-2, 2), (2, -2)))
    t = f.bits(_p(p, "D"), 8).bits(_p(p, "F"), 8).extent(107_000).timings()
    return Signal(36000, [], t)


def mce(p: dict) -> Signal:
    # {36k,444,msb}<-1,1|1,-1>(6,-2,1:1,6:3,-2,2,OEM1:8,OEM2:8,T:1,D:7,F:8,^107m)*{OEM1=128,OEM2=12}
    f = _Frame(444, (-1, 1), (1, -1), msb=True).d(6, -2).bits(1, 1).bits(6, 3).d(-2, 2)
    t = (f.bits(128, 8).bits(_p(p, "S", 12), 8).bits(_p(p, "T", 0), 1).bits(_p(p, "D"), 7)
         .bits(_p(p, "F"), 8).extent(107_000).timings())
    return Signal(36000, [], t)


# --- Set-top box protocols ---------------------------------------------------

def dish(p: dict) -> Signal:
    # {57.6k,400}<1,-7|1,-4>(1,-15,(F:-6,U:5,D:5,1,-15)+)   (F:-6 = 6 bits msb first)
    body = (_Frame(400, (1, -7), (1, -4)).bits(_p(p, "F"), 6, msb=True).bits(_p(p, "S", 0), 5)
            .bits(_p(p, "D"), 5).d(1, -15).timings())
    return Signal(57600, [400, 6000] + body, body)


def gi_cable(p: dict) -> Signal:
    # {38.7k,490}<1,-4.5|1,-9>(18,-9,F:8,D:4,C:4,1,-84,(18,-4.5,1,-178)*){C=-(D+F:4+F:4:4)}
    D, F = _p(p, "D"), _p(p, "F")
    C = (-(D + (F & 0xF) + (F >> 4))) & 0xF
    intro = _Frame(490, (1, -4.5), (1, -9)).d(18, -9).bits(F, 8).bits(D, 4).bits(C, 4).d(1, -84).timings()
    rep = _Frame(490, (1, -4.5), (1, -9)).d(18, -4.5, 1, -178).timings()
    return Signal(38700, intro, rep)


def directv(p: dict) -> Signal:
    # {38k,600,msb}<1,-1|1,-2|2,-1|2,-2>(5,(5,-2,D:4,F:8,C:4,1,-50)+){C=7*(F:2:6)+5*(F:2:4)+3*(F:2:2)+(F:2)}
    D, F = _p(p, "D"), _p(p, "F")
    C = (7 * ((F >> 6) & 3) + 5 * ((F >> 4) & 3) + 3 * ((F >> 2) & 3) + (F & 3)) & 0xF
    spec = ((1, -1), (1, -2), (2, -1), (2, -2))
    word = (D << 12) | (F << 4) | C
    body = _Frame(600, (1, -1), (1, -2)).d(5, -2).symbols(word, 8, spec, 2).d(1, -50).timings()
    # The extra leading 5-unit flash merges with the first frame's header flash.
    return Signal(38000, [body[0] + 3000] + body[1:], body)


def nokia32(p: dict) -> Signal:
    # {36k,msb}<164,-276|164,-445|164,-614|164,-783>(412,-276,D:8,S:8,X:8,F:8,164,^100m)+
    spec = ((164, -276), (164, -445), (164, -614), (164, -783))
    word = (_p(p, "D") << 24) | (_p(p, "S", 0) << 16) | (_p(p, "X", 0) << 8) | _p(p, "F")
    t = _Frame(1, (0,), (0,)).us(412, -276).symbols(word, 16, spec, 2).us(164).extent(100_000).timings()
    return Signal(36000, t, t)


# --- Dispatch ----------------------------------------------------------------

_ALIASES = {
    "NEC": "NEC1", "NEC1": "NEC1", "NEC2": "NEC2", "NECX1": "NECx1", "NECX2": "NECx2",
    "NEC1-Y1": "NEC1-y1", "NEC1-Y2": "NEC1-y2", "NEC1-Y3": "NEC1-y3",
    "NEC2-Y1": "NEC2-y1", "NEC2-Y2": "NEC2-y2", "NEC2-Y3": "NEC2-y3",
    "NEC1-F16": "NEC1-f16", "NEC2-F16": "NEC2-f16", "NECX1-F16": "NECx1-f16", "NECX2-F16": "NECx2-f16",
}


def encode(protocol: str, params: dict) -> Signal:
    """Encode an IRDB/IRP-style protocol name with D/S/F parameters."""
    name = protocol.strip()
    up = name.upper().replace("_", "-")
    if up in _ALIASES:
        return nec(_ALIASES[up], params)
    if up in ("SONY12", "SONY15", "SONY20"):
        return sony(int(up[4:]), params)
    if up == "RC5":
        return rc5(params)
    if up == "RC6":
        return rc6(params)
    if up == "MCE":
        return mce(params)
    if up in ("JVC", "JVC{2}"):
        return jvc(params, 2 if "{2}" in up else 1)
    if up == "PANASONIC":
        return panasonic(params)
    if up == "PANASONIC-OLD":
        return panasonic_old(params)
    if up == "DENON":
        return sharp_like(params, 38000, 0, 3)
    if up == "DENON-K":
        return denon_k(params)
    if up in ("SHARP", "SHARP{1}"):
        return sharp_like(params, 38000, 1, 2)
    if up == "MITSUBISHI":
        return mitsubishi(params)
    if up == "AIWA":
        return nec42(params)
    if up == "PIONEER":
        return pioneer(params)
    if up == "PROTON":
        return proton(params)
    if up == "RCA":
        return rca(params)
    if up == "RCA-38":
        return rca(params, 38700)
    if up == "RECS80":
        return recs80(params)
    if up == "DISH-NETWORK":
        return dish(params)
    if up == "G.I.CABLE":
        return gi_cable(params)
    if up == "DIRECTV":
        return directv(params)
    if up == "NOKIA32":
        return nokia32(params)
    if up == "FUJITSU":
        return fujitsu(params)
    if up == "AKAI":
        return akai(params)
    raise UnsupportedProtocol(name)


def encode_flipper(protocol: str, address: int, command: int) -> Signal:
    """Encode a Flipper Zero 'parsed' signal (address/command are little-endian ints)."""
    if protocol == "NEC":
        return nec("NEC1", {"D": address & 0xFF, "F": command & 0xFF})
    if protocol == "NECext":
        return nec("NEC1-f16", {"D": address & 0xFF, "S": (address >> 8) & 0xFF, "F": command & 0xFFFF})
    if protocol == "NEC42":
        return nec42({"D": address & 0xFF, "S": (address >> 8) & 0x1F, "F": command & 0xFF})
    if protocol == "Samsung32":
        return nec("NECx2", {"D": address & 0xFF, "S": address & 0xFF, "F": command & 0xFF})
    if protocol == "RC5":
        return rc5({"D": address & 0x1F, "F": command & 0x3F})
    if protocol == "RC5X":
        return rc5({"D": address & 0x1F, "F": (command & 0x3F) | 0x40})
    if protocol == "RC6":
        return rc6({"D": address & 0xFF, "F": command & 0xFF})
    if protocol == "SIRC":
        return sony(12, {"D": address & 0x1F, "F": command & 0x7F})
    if protocol == "SIRC15":
        return sony(15, {"D": address & 0xFF, "F": command & 0x7F})
    if protocol == "SIRC20":
        return sony(20, {"D": address & 0x1F, "S": (address >> 5) & 0xFF, "F": command & 0x7F})
    if protocol == "RCA":
        return rca({"D": address & 0xF, "F": command & 0xFF})
    if protocol == "Pioneer":
        return pioneer({"D": address & 0xFF, "F": command & 0xFF})
    if protocol == "Kaseikyo":
        return flipper_kaseikyo(address, command)
    raise UnsupportedProtocol(protocol)
