#!/usr/bin/env bash
# Clone (or update) the upstream IR databases into pipeline/sources/.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p sources

fetch() {
  local url=$1 dir=sources/$2
  if [ -d "$dir/.git" ]; then
    git -C "$dir" pull --ff-only --depth 1
  else
    git clone --depth 1 "$url" "$dir"
  fi
}

fetch https://github.com/Lucaslhm/Flipper-IRDB.git flipper-irdb
fetch https://github.com/probonopd/irdb.git irdb
fetch https://github.com/crankyoldgit/IRremoteESP8266.git irremoteesp8266
