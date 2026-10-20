#!/usr/bin/env bash
# Restarts the dev server (optionally on a fresh world) and waits for RCON.
#   --fresh             start the world over
#   OCEAN=1 ...         use a normally generated world (real oceans) instead of the flat test world
#   COMPAT=1 ...        also load the optional mods Seafarers works with (JEI, EMI, JourneyMap, OPAC, Simple Voice Chat)
cd "$(dirname "$0")/.."
python tools/rcon.py stop >/dev/null 2>&1; sleep 6
if [ -n "$OCEAN" ]; then
    sed -i 's/^level-name=.*/level-name=ocean/; s/^level-type=.*/level-type=minecraft\\:normal/; s/^level-seed=.*/level-seed=5/' run/server/server.properties
    [ "$1" = "--fresh" ] && rm -rf run/server/ocean
else
    sed -i 's/^level-name=.*/level-name=world/; s/^level-type=.*/level-type=minecraft\\:flat/; s/^level-seed=.*/level-seed=/' run/server/server.properties
    [ "$1" = "--fresh" ] && rm -rf run/server/world
fi
EXTRA=""; [ -n "$COMPAT" ] && EXTRA="-PcompatTest"; [ -n "$NOEMI" ] && EXTRA="$EXTRA -PnoEmi"
(./gradlew.bat runServer $EXTRA --console=plain > run/server/console.log 2>&1 &)
for i in $(seq 1 300); do grep -qE "RCON running|BUILD FAILED" run/server/console.log 2>/dev/null && break; sleep 1; done
grep -E "RCON running|BUILD FAILED" run/server/console.log
