# Isolated real-server smoke matrix

Requires Linux x64, Python 3, curl, tar and bash. Run commands from this directory.
These scripts work inside their own directory. They download official Paper and
Adoptium artifacts, verify SHA256, and create local test worlds. Servers bind only
to 127.0.0.1; online-mode remains enabled. Minecraft EULA acceptance is explicit.
No external player or client is needed.

1. `python prepare-runtime.py`
2. Read https://www.minecraft.net/eula. Only if accepted:
   `python run-matrix.py --accept-eula`
3. `bash build-harness.sh`
4. Copy the final shaded plugin JAR to `Commandant-under-test.jar`.
5. `python run-matrix.py --accept-eula Commandant-under-test.jar`

Review `smoke-results.json` and every `server-*/smoke.log`. Every version must
have ready=true, exit=0, smoke_pass=true and the `COMMANDANT_SMOKE_PASS` marker.
The runner prints the exact artifact SHA256 before each server invocation.
Official download URLs and expected SHA256 are in `artifact-manifest.json`.

Coverage: real Bukkit registration and command dispatch, PlayerCommandPreprocess
listener with a dynamic Player proxy, original example rules and Kether tell/cancel,
permission/no-permission, replacement placeholders, priorities, disabled rules,
explicit admin reload, byte-preserved user YAML, JavaScript conditions including
Java.type/HashMap interop, plugin disable and listener cleanup. Config files are
restored after tests. It is a deterministic API smoke test, not a full player
protocol/session test, exhaustive plugin interoperability test, or Folia test.

Hosts: the eight pinned builds and their JDK major versions are declared in matrix.json.
Use --versions 1.12,26.2 to run a selected subset.
The matrix pins Paper builds; downloaded JDK metadata records exact versions and hashes. Java cannot resolve external service hosts
in some cloud environments; the preparer prefetches official Mojang bootstrap
jars with their embedded expected hashes. Mojang authentication-key and update
checks may still log network errors; these do not mean plugin assertions failed.

The harness builder also creates a synthetic NonKetherFixture, loaded only on 26.2.
It exercises optional protocol discovery without pretending to be a real third-party Kether extension.
