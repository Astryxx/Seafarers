#!/usr/bin/env bash
# Visual check: restarts server + client on a fresh test sea, launches a sloop and takes screenshots.
# usage: tools/look.sh  (shots land in shots/look_*.png)
cd "$(dirname "$0")/.."
pid=$(um win ps | grep "Minecraft NeoForge" | awk '{print $1}')
[ -n "$pid" ] && um win kill "$pid" >/dev/null
tools/restart.sh --fresh
python tools/scenario.py "0:forceload add -16 -16 112 272" "0:seafarers test sea 0 100 0" "1:seafarers wind 0" \
  "1:time set 6000" "1:gamerule doDaylightCycle false" "2:seafarers sloop 48 108 140 west" \
  "4:execute positioned 48 110 140 run seafarers ship controls 0 3 20 true" --wait 1 >/dev/null
(./gradlew.bat runClientJoin --console=plain > run/client-console.log 2>&1 &)
for i in $(seq 1 300); do grep -q "Dev joined the game" run/server/console.log && break; sleep 1; done
sleep 6
python tools/rcon.py "gamemode spectator Dev" >/dev/null
# view <x starboard> <y up> <z toward bow> <yaw off the bow> <pitch>, all relative to the helm
shot() { python tools/rcon.py "execute as Dev at Dev run seafarers ship view $2" >/dev/null; sleep 3; um win shot --title "Minecraft NeoForge" --scale 0.5 "shots/look_$1.png" >/dev/null 2>&1; echo "shots/look_$1.png"; }
python tools/rcon.py "tp Dev 48 115 140" >/dev/null
shot quarter "-15 4 6 125 10"
shot side "-20 0 3 90 0"
shot bow "-6 -2 18 145 -5"
shot stern "9 3 -14 -35 12"
shot deck "0 2.5 4 0 15"
shot cabin "0 -3 -6 0 5"
shot gallery "0 -3 -2 180 8"
shot hold_fwd "0 -5 4 0 5"
shot hold_aft "0 -5 11 180 5"
