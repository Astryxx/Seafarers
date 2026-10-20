#!/usr/bin/env bash
# Second look at the fittings, reusing the running client (run tools/look_fittings.sh first).
cd "$(dirname "$0")/.."
shot() { python tools/rcon.py "execute as Dev at Dev run seafarers ship view $2" >/dev/null; sleep 3; um win shot --title "Minecraft NeoForge" --scale 0.5 "shots/fit_$1.png" >/dev/null 2>&1; echo "shots/fit_$1.png"; }
python tools/rcon.py "kill @e[type=seafarers:harpoon]" "execute positioned 30 110 180 run seafarers ship remove" "time set 6000" "tp Dev 48 115 140" >/dev/null
sleep 2
shot flag "-10 19 6 90 -12"
shot maptable2 "0 -3 -4.3 180 55"
shot harpoons2 "1 -2 7 0 18"
shot stove2 "-0.2 -5 13.5 0 35"
python tools/rcon.py "tp Dev 48 110 152" >/dev/null
sleep 1
shot rowboat "-3.5 1.5 -2.5 40 28"
