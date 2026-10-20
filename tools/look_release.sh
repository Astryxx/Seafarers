#!/usr/bin/env bash
# Screenshots of the finished fittings and screens. Shots land in shots/rel_*.png
cd "$(dirname "$0")/.."
pid=$(um win ps | grep "Minecraft NeoForge" | awk '{print $1}')
[ -n "$pid" ] && um win kill "$pid" >/dev/null
tools/restart.sh --fresh
at() { echo "execute positioned 48 110 140 run seafarers $1"; }
python tools/scenario.py "0:forceload add -16 -16 112 272" "0:seafarers test sea 0 100 0" "1:seafarers wind 0" \
  "1:time set 6000" "1:gamerule doDaylightCycle false" "2:seafarers sloop 48 108 140 west" \
  "4:$(at 'ship controls 0 0 0 true')" --wait 1 >/dev/null
(./gradlew.bat runClientJoin --console=plain > run/client-console.log 2>&1 &)
for i in $(seq 1 300); do grep -q "Dev joined the game" run/server/console.log && break; sleep 1; done
sleep 8
python tools/rcon.py "gamemode spectator Dev" "tp Dev 48 115 140" "$(at 'ship stove 1900')" "$(at 'ship flag')" >/dev/null
shot() { python tools/rcon.py "execute as Dev at Dev run seafarers ship view $2" >/dev/null; sleep 3; um win shot --title "Minecraft NeoForge" --scale 0.5 "shots/rel_$1.png" >/dev/null 2>&1; echo "shots/rel_$1.png"; }
shot bow "0 -1.6 9 0 14"
shot harpoon_side "-3.5 -1 12.5 60 25"
shot galley "-0.5 -5 4.5 40 28"
shot water "-0.2 -5.2 6.5 70 40"
shot stove_top "0.6 -4.6 7.4 80 60"
python tools/rcon.py "setblock 30 110 120 minecraft:oak_planks" "setblock 30 111 120 seafarers:water_barrel[level=2]" "setblock 31 110 120 minecraft:oak_planks" "setblock 31 111 120 seafarers:water_barrel[level=0]" "tp Dev 29 113.5 118 -40 45" >/dev/null; sleep 3
um win shot --title "Minecraft NeoForge" --scale 0.5 shots/rel_land.png >/dev/null 2>&1; echo shots/rel_land.png
python tools/rcon.py "tp Dev 48 115 140" >/dev/null
python tools/rcon.py "gamemode creative Dev" "item replace entity Dev weapon.mainhand with seafarers:grog_bucket" >/dev/null
shot grog "-0.5 -5 2.5 -30 20"
python tools/rcon.py "item replace entity Dev weapon.mainhand with minecraft:air" "$(at 'ship open voyage Dev')" >/dev/null; sleep 3
um win shot --title "Minecraft NeoForge" --scale 0.5 shots/rel_voyage.png >/dev/null 2>&1; echo shots/rel_voyage.png
