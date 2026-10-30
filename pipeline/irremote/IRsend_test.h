// Replacement for IRremoteESP8266's test/IRsend_test.h, found first on the
// include path when building gen_ac. Every A/C class sends through an
// IRsendTest member under UNIT_TEST; this one records the timings globally.
#ifndef TEST_IRSEND_TEST_H_
#define TEST_IRSEND_TEST_H_

#include <stdint.h>
#include <vector>
#include "IRrecv.h"
#include "IRsend.h"

extern std::vector<int32_t> g_timings;  // + mark, - space (microseconds)
extern uint32_t g_freq;

class IRsendTest : public IRsend {
 public:
  explicit IRsendTest(uint16_t x, bool i = false, bool j = true) : IRsend(x, i, j) {}

  // Referenced by IRac.cpp's unit-test decode hook (never active here).
  decode_results capture;
  void makeDecodeResult(uint16_t = 0) {}

  uint16_t mark(uint16_t usec) override {
    g_freq = _freq_unittest;
    if (usec) g_timings.push_back(usec);
    return 0;
  }

  void space(uint32_t time) override {
    if (time) g_timings.push_back(-static_cast<int32_t>(time));
  }
};

#endif  // TEST_IRSEND_TEST_H_
