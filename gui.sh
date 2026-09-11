#!/usr/bin/env bash
# Launch the MH Wilds loadout optimizer GUI (offline-safe classpath build).
set -euo pipefail
cd "$(dirname "$0")"

mvn compile -q
mvn dependency:build-classpath -q -Dmdep.outputFile=target/classpath.txt

CP="target/classes:$(cat target/classpath.txt)"
exec java -cp "$CP" mhwilds.optimizer.gui.App "$@"