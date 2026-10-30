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

    stdAc::state_t off = base;
    off.power = false;
    emit(name, &ac, off, "Tắt", "power=0");

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
            emit(name, &ac, s, label, params);
          }
        }
      }
    }
    for (const Fan& f : kFans) {
      stdAc::state_t s = base;
      s.power = true;
      s.mode = stdAc::opmode_t::kFan;
      s.fanspeed = f.fan;
      emit(name, &ac, s, std::string("Quạt, ") + f.label,
           std::string("power=1,mode=fan,temp=0,fan=") + f.key + ",swing=0");
    }
  }
  return 0;
}
