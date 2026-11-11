#!/bin/bash
# Run the original 2011 Java Encircle locally: game server + matchmaker +
# one AI opponent + your client window. Needs Java 8 in .tools/jdk8
# (see README). Close the game window (or Ctrl-C) to stop everything.
#
#   tools/run-old-game.sh            # steer with the mouse (how the 2011 site played)
#   tools/run-old-game.sh keyboard   # arrows + space instead
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="$ROOT/.tools/jdk8/bin/java"
BUILD="$ROOT/.tools/oldbuild"
OLD="$ROOT/encircle-old"
[ -x "$JAVA" ] || { echo "Java 8 not found at $JAVA"; exit 1; }

# Compile the old source once.
if [ ! -f "$BUILD/aoi/Client.class" ]; then
  mkdir -p "$BUILD"
  "$ROOT/.tools/jdk8/bin/javac" -nowarn -encoding ISO-8859-1 -g -classpath "$OLD/lib/commons-codec-1.5.jar" \
    -d "$BUILD" "$OLD"/src/aoi/*.java "$OLD"/src/unreliablestream/*.java || exit 1
fi

CP="$BUILD:$OLD:$OLD/lib/commons-codec-1.5.jar"
LOG="$ROOT/.tools/oldrun"
mkdir -p "$LOG" "$OLD/out" "$OLD/segmentation/game_output/fly"
cd "$OLD"

USE_MOUSE=true
[ "${1:-}" = "keyboard" ] && USE_MOUSE=false
CONF="$LOG/client.conf"
printf '# server:\nlocalhost\n# server port:\n7601\n# TCP only?\nfalse\n# non-human:\nfalse\n# client side prediction time in ms (set to 0 to disable):\n200\n# use mouse:\n%s\n' "$USE_MOUSE" > "$CONF"

PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null; wait 2>/dev/null; echo "stopped."; }
trap cleanup EXIT INT TERM

"$JAVA" -cp "$CP" aoi.GameServer < gameserver.conf > "$LOG/gameserv.out" 2>&1 & PIDS+=($!)
"$JAVA" -cp "$CP" aoi.Matchmaker > "$LOG/matchmaker.out" 2>&1 & PIDS+=($!)
sleep 3
"$JAVA" -cp "$CP" aoi.Client < ai.conf > "$LOG/ai.out" 2>&1 & PIDS+=($!)
sleep 2
echo "Starting your client (mouse: $USE_MOUSE). Logs in $LOG"
"$JAVA" -cp "$CP" aoi.Client < "$CONF" > "$LOG/client.out" 2>&1
