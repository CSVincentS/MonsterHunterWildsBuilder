#!/usr/bin/env bash
# Run the MH Wilds loadout optimizer.
set -euo pipefail
cd "$(dirname "$0")"

mvn compile -q
mvn dependency:build-classpath -q -Dmdep.outputFile=target/classpath.txt

CP="target/classes:$(cat target/classpath.txt)"
exec java -cp "$CP" mhwilds.optimizer.cli.Main "$@"