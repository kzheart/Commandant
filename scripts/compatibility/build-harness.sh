#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f server-1.12.2/cache/patched_1.12.2.jar ]]; then
  echo 'Run the baseline matrix first to generate Paper 1.12.2 API classes.' >&2
  exit 1
fi
mkdir -p harness/classes
cat > harness/classes/plugin.yml <<'YAML'
name: CommandantSmoke
version: 1.0
main: smoke.CommandantSmoke
depend: [Commandant]
YAML
./java8/bin/javac -encoding UTF-8 -source 8 -target 8 -cp server-1.12.2/cache/patched_1.12.2.jar -d harness/classes harness/CommandantSmoke.java
./java8/bin/jar cf CommandantSmoke.jar -C harness/classes .
sha256sum CommandantSmoke.jar

mkdir -p fixture/classes
cat > fixture/classes/plugin.yml <<'YAML'
name: NonKetherFixture
version: 1.0
main: fixture.platform.BukkitPlugin
loadbefore: [Commandant]
YAML
./java8/bin/javac -encoding UTF-8 -source 8 -target 8 -cp server-1.12.2/cache/patched_1.12.2.jar -d fixture/classes fixture/src/fixture/platform/BukkitPlugin.java fixture/src/fixture/common/OpenAPI.java
./java8/bin/jar cf NonKetherFixture.jar -C fixture/classes .
