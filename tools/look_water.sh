#!/usr/bin/env bash
# Visual check of where the sea meets the hull, outside and in. Shots land in shots/water_*.png
cd "$(dirname "$0")/.."
pid=$(um win ps | grep "Minecraft NeoForge" | awk '{print $1}')
[ -n "$pid" ] && um win kill "$pid" >/dev/null
tools/restart.sh --fresh
python tools/scenario.py "0:forceload add -16 -16 112 272" "0:seafarers test sea 0 100 0" "1:seafarers wind 0" \
  "1:time set 6000" "1:gamerule doDaylightCycle false" "2:seafarers sloop 48 108 140 west" \
  "4:execute positioned 48 110 140 run seafarers ship controls 0 0 0 true" --wait 1 >/dev/null
(./gradlew.bat runClientJoin --console=plain > run/client-console.log 2>&1 &)
for i in $(seq 1 300); do grep -q "Dev joined the game" run/server/console.log && break; sleep 1; done
sleep 8
python tools/rcon.py "gamemode spectator Dev" "tp Dev 48 115 140" >/dev/null
shot() { python tools/rcon.py "execute as Dev at Dev run seafarers ship view $2" >/dev/null; sleep 3; um win shot --title "Minecraft NeoForge" --scale 0.5 "shots/water_$1.png" >/dev/null 2>&1; echo "shots/water_$1.png"; }
shot bow "-4 -3 19 150 30"
shot side "-7 -3.5 6 70 30"
shot stern "4 -3 -4 -40 30"
shot hold "0 -5 9 0 25"
shot hold_wall "-1 -5 13 -60 35"
python tools/rcon.py "seafarers launch rowboat 48 108 160 west" >/dev/null; sleep 7
python tools/rcon.py "tp Dev 44 111 162 -30 35" >/dev/null; sleep 3
um win shot --title "Minecraft NeoForge" --scale 0.5 shots/water_rowboat.png >/dev/null 2>&1; echo shots/water_rowboat.png
python tools/rcon.py "tp Dev 49 117 160.5 0 90" >/dev/null; sleep 3
um win shot --title "Minecraft NeoForge" --scale 0.5 shots/water_rowboat_top.png >/dev/null 2>&1; echo shots/water_rowboat_top.png
python tools/rcon.py "tp Dev 48 132 140 0 90" >/dev/null; sleep 3
um win shot --title "Minecraft NeoForge" --scale 0.5 shots/water_sloop_top.png >/dev/null 2>&1; echo shots/water_sloop_top.png
# liveries and rigging
python tools/rcon.py "tp Dev 48 115 140" "execute positioned 48 110 140 run seafarers ship paint hull red" "execute positioned 48 110 140 run seafarers ship paint sails black" "execute positioned 48 110 140 run seafarers ship controls 0 3 25 true" >/dev/null; sleep 4
shot livery "-16 4 4 105 8"
shot rigging "-6 6 9 80 -20"
shot canopy "-6 1 -3 60 5"
