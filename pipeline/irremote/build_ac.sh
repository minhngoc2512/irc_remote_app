#!/usr/bin/env bash
# Compile IRremoteESP8266's A/C encoders for the host and dump A/C states to
# pipeline/build/irremoteesp8266_ac.tsv (read by build_db.py).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
LIB=$HERE/../sources/irremoteesp8266/src
OUT=$HERE/../build
mkdir -p "$OUT/obj"

FLAGS=(-std=gnu++11 -O1 -w -DUNIT_TEST -D_IR_LOCALE_=en-AU -I"$HERE" -I"$LIB")
objs=()
for src in "$LIB"/*.cpp "$HERE/gen_ac.cpp"; do
  obj=$OUT/obj/$(basename "${src%.cpp}").o
  if [ ! -f "$obj" ] || [ "$src" -nt "$obj" ] || [ "$HERE/IRsend_test.h" -nt "$obj" ]; then
    g++ "${FLAGS[@]}" -c "$src" -o "$obj" &
  fi
  objs+=("$obj")
  while [ "$(jobs -r | wc -l)" -ge "$(nproc)" ]; do wait -n; done
done
wait
g++ -o "$OUT/gen_ac" "${objs[@]}"
"$OUT/gen_ac" > "$OUT/irremoteesp8266_ac.tsv"
echo "$(cut -f1 "$OUT/irremoteesp8266_ac.tsv" | sort -u | wc -l) protocols, $(wc -l < "$OUT/irremoteesp8266_ac.tsv") states -> $OUT/irremoteesp8266_ac.tsv"
