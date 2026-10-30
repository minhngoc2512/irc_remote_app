// Generates A/C remote states for every protocol IRac supports.
// Output (TSV, one line per state):
//   protocol  label  params  freq  signed-timings(csv)
#include <iostream>
#include <string>
#include <vector>

#include "IRac.h"
#include "IRutils.h"

std::vector<int32_t> g_timings;
uint32_t g_freq = 38000;

namespace {

struct Mode {
  stdAc::opmode_t mode;
  const char* key;
  const char* label;
};
struct Fan {
  stdAc::fanspeed_t fan;
  const char* key;
  const char* label;
};

const Mode kModes[] = {
    {stdAc::opmode_t::kCool, "cool", "Lạnh"},
    {stdAc::opmode_t::kHeat, "heat", "Sưởi"},
    {stdAc::opmode_t::kDry, "dry", "Hút ẩm"},
    {stdAc::opmode_t::kAuto, "auto", "Tự động"},
};
const Fan kFans[] = {
    {stdAc::fanspeed_t::kAuto, "auto", "gió tự động"},
    {stdAc::fanspeed_t::kLow, "low", "gió thấp"},
    {stdAc::fanspeed_t::kMedium, "medium", "gió vừa"},
    {stdAc::fanspeed_t::kHigh, "high", "gió cao"},
};

void emit(const std::string& proto, IRac* ac, stdAc::state_t s, const std::string& label,
          const std::string& params) {
  g_timings.clear();
  if (!ac->sendAc(s, nullptr) || g_timings.empty()) return;
  std::cout << proto << '\t' << label << '\t' << params << '\t' << g_freq << '\t';
  for (size_t i = 0; i < g_timings.size(); i++) std::cout << (i ? "," : "") << g_timings[i];
  std::cout << '\n';
}

struct Model {
  decode_type_t protocol;
  int16_t value;
  const char* name;
};

// Protocols whose encoding differs per remote model (enums in IRsend.h).
// Each model becomes its own codeset; other protocols use the default model.
const Model kModels[] = {
    {decode_type_t::PANASONIC_AC, kPanasonicLke, "LKE"},
    {decode_type_t::PANASONIC_AC, kPanasonicNke, "NKE"},
    {decode_type_t::PANASONIC_AC, kPanasonicDke, "DKE"},
    {decode_type_t::PANASONIC_AC, kPanasonicJke, "JKE"},
    {decode_type_t::PANASONIC_AC, kPanasonicCkp, "CKP"},
    {decode_type_t::PANASONIC_AC, kPanasonicRkr, "RKR"},
    {decode_type_t::FUJITSU_AC, ARRAH2E, "ARRAH2E"},
    {decode_type_t::FUJITSU_AC, ARDB1, "ARDB1"},
    {decode_type_t::FUJITSU_AC, ARREB1E, "ARREB1E"},
    {decode_type_t::FUJITSU_AC, ARJW2, "ARJW2"},
    {decode_type_t::FUJITSU_AC, ARRY4, "ARRY4"},
    {decode_type_t::FUJITSU_AC, ARREW4E, "ARREW4E"},
    {decode_type_t::GREE, YAW1F, "YAW1F"},
    {decode_type_t::GREE, YBOFB, "YBOFB"},
    {decode_type_t::GREE, YX1FSF, "YX1FSF"},
    {decode_type_t::HAIER_AC176, V9014557_A, "V9014557-A"},
    {decode_type_t::HAIER_AC176, V9014557_B, "V9014557-B"},
    {decode_type_t::HITACHI_AC1, R_LT0541_HTA_A, "R-LT0541-HTA-A"},
    {decode_type_t::HITACHI_AC1, R_LT0541_HTA_B, "R-LT0541-HTA-B"},
    {decode_type_t::MIRAGE, KKG9AC1, "KKG9A-C1"},
    {decode_type_t::MIRAGE, KKG29AC1, "KKG29A-C1"},
    {decode_type_t::SHARP_AC, A907, "A907"},
    {decode_type_t::SHARP_AC, A705, "A705"},
    {decode_type_t::SHARP_AC, A903, "A903"},
    {decode_type_t::TCL112AC, TAC09CHSD, "TAC09CHSD"},
    {decode_type_t::TCL112AC, GZ055BE1, "GZ055BE1"},
    {decode_type_t::WHIRLPOOL_AC, DG11J13A, "DG11J13A"},
    {decode_type_t::WHIRLPOOL_AC, DG11J191, "DG11J191"},
    {decode_type_t::LG, GE6711AR2853M, "GE6711AR2853M"},
    {decode_type_t::LG, LG6711A20083V, "6711A20083V"},
    {decode_type_t::LG2, AKB75215403, "AKB75215403"},
    {decode_type_t::LG2, AKB74955603, "AKB74955603"},
    {decode_type_t::LG2, AKB73757604, "AKB73757604"},
    {decode_type_t::ARGO, SAC_WREM2, "WREM2"},
    {decode_type_t::ARGO, SAC_WREM3, "WREM3"},
    {decode_type_t::TOSHIBA_AC, kToshibaGenericRemote_A, "remote-A"},
    {decode_type_t::TOSHIBA_AC, kToshibaGenericRemote_B, "remote-B"},
};

void emit_all(IRac* ac, const std::string& name, stdAc::state_t base);

}  // namespace

int main() {
  IRac ac(4);
  for (int p = 0; p <= kLastDecodeType; p++) {
    const auto proto = static_cast<decode_type_t>(p);
    if (!IRac::isProtocolSupported(proto)) continue;
    const std::string name = typeToString(proto).c_str();

    stdAc::state_t base;
    base.protocol = proto;
    base.celsius = true;
    bool has_models = false;
    for (const Model& m : kModels) {
      if (m.protocol != proto) continue;
      has_models = true;
      base.model = m.value;
      emit_all(&ac, name + "#" + m.name, base);
    }
    if (!has_models) emit_all(&ac, name, base);
  }
  return 0;
}

namespace {

void emit_all(IRac* ac, const std::string& name, stdAc::state_t base) {

  stdAc::state_t off = base;
  off.power = false;
  emit(name, ac, off, "Tắt", "power=0");

  for (const Mode& m : kModes) {
    for (const Fan& f : kFans) {
      // Every temperature with automatic fan; fixed fan speeds for cooling only.
      if (f.fan != stdAc::fanspeed_t::kAuto && m.mode != stdAc::opmode_t::kCool) continue;
      for (int t = 16; t <= 30; t++) {
        for (int swing = 0; swing <= (m.mode == stdAc::opmode_t::kCool && f.fan == stdAc::fanspeed_t::kAuto); swing++) {
          stdAc::state_t s = base;
          s.power = true;
          s.mode = m.mode;
          s.degrees = t;
          s.fanspeed = f.fan;
          s.swingv = swing ? stdAc::swingv_t::kAuto : stdAc::swingv_t::kOff;
          std::string label = std::string(m.label) + " " + std::to_string(t) + "°C, " + f.label +
                              (swing ? ", đảo gió" : "");
          std::string params = std::string("power=1,mode=") + m.key + ",temp=" + std::to_string(t) +
                               ",fan=" + f.key + ",swing=" + std::to_string(swing);
          emit(name, ac, s, label, params);
        }
      }
    }
  }
  for (const Fan& f : kFans) {
    stdAc::state_t s = base;
    s.power = true;
    s.mode = stdAc::opmode_t::kFan;
    s.fanspeed = f.fan;
    emit(name, ac, s, std::string("Quạt, ") + f.label,
         std::string("power=1,mode=fan,temp=0,fan=") + f.key + ",swing=0");
  }
}

}  // namespace
