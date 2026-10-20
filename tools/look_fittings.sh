#!/usr/bin/env bash
# Visual check of the fittings: flag, stove, map table, harpoons, lanterns, and a rowboat docked alongside.
# usage: tools/look_fittings.sh  (shots land in shots/fit_*.png)
cd "$(dirname "$0")/.."
pid=$(um win ps | grep "Minecraft NeoForge" | awk '{print $1}')
[ -n "$pid" ] && um win kill "$pid" >/dev/null
tools/restart.sh --fresh
at() { echo "execute positioned 48 110 140 run seafarers $1"; }
python tools/scenario.py "0:forceload add -16 -16 112 272" "0:seafarers test sea 0 100 0" "1:seafarers wind 0" \
  "1:time set 6000" "1:gamerule doDaylightCycle false" "2:seafarers sloop 48 108 140 west" \
  "4:$(at 'ship controls 0 0 20 true')" --wait 1 >/dev/null
(./gradlew.bat runClientJoin --console=plain > run/client-console.log 2>&1 &)
for i in $(seq 1 300); do grep -q "Dev joined the game" run/server/console.log && break; sleep 1; done
sleep 6
python tools/rcon.py "gamemode spectator Dev" "tp Dev 48 115 140" "$(at 'ship flag')" "$(at 'ship stove 2000')" "$(at 'ship maptable')" >/dev/null
shot() { python tools/rcon.py "execute as Dev at Dev run seafarers ship view $2" >/dev/null; sleep 3; um win shot --title "Minecraft NeoForge" --scale 0.5 "shots/fit_$1.png" >/dev/null 2>&1; echo "shots/fit_$1.png"; }
# view <x starboard> <y up> <z toward bow> <yaw off the bow> <pitch>, relative to the helm
shot masthead "-9 8 4 60 -25"
shot aftdeck "0 1.6 -3.5 20 25"
shot stove "0 -4.4 6.5 0 30"
shot maptable "0 -1.4 -3.6 180 45"
shot harpoons "0 0.4 6 0 10"
python tools/rcon.py "$(at 'ship lanterns toggle')" "time set 18000" >/dev/null
shot night "-12 3 4 110 10"
python tools/rcon.py "$(at 'ship lanterns toggle')" >/dev/null
shot night_lit "-12 3 4 110 10"
python tools/rcon.py "time set 6000" "seafarers launch rowboat 50 108 150 west" >/dev/null
sleep 6
python tools/rcon.py "execute positioned 48 110 150 run seafarers ship dock 20" >/dev/null
sleep 10
shot docked "-12 2 3 115 18"
python tools/rcon.py "tp Dev 30 115 170" "seafarers launch rowboat 30 108 180 west" >/dev/null
sleep 6
python tools/rcon.py "execute positioned 30 110 180 run seafarers ship row 1 0 600" "tp Dev 30 115 180" >/dev/null
sleep 2
shot rowing "-3 2.5 -4 30 30"
