#!/usr/bin/env bash
# Connect to the phone over Wireless Debugging, install the debug APK, and launch it.
#
# Usage:
#   ./run-on-phone.sh               # auto-discover the phone over mDNS, install, launch
#   ./run-on-phone.sh <port>        # connect to $PHONE_IP:<port>
#   ./run-on-phone.sh <ip>:<port>   # use a different IP
#
# Wireless Debugging picks a new ip:port every time it is toggled or the phone reboots,
# so auto-discovery is the default: the phone announces itself over mDNS and there is
# nothing to read off its screen. Pairing is a separate, persistent thing — when the phone
# drops it, connecting fails on an open port, and the script prints how to restore it.

set -euo pipefail

PKG="com.resolveprogramming.pocketcounter"
DEFAULT_IP="${PHONE_IP:-192.168.50.14}"
# Variant to install. Override e.g. VARIANT=installLocalDebug to target the local backend.
VARIANT="${VARIANT:-installDevDebug}"

# Make sure adb is reachable even from a shell that didn't load the SDK on PATH.
if ! command -v adb >/dev/null 2>&1; then
  export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
fi

cd "$(dirname "$0")"

has_device() {
  adb devices | awk 'NR>1 && $2=="device"{found=1} END{exit !found}'
}

# The phone advertises _adb-tls-connect._tcp while Wireless Debugging is on. The first ask
# can come back empty while the browser is still warming up, so try a few times.
discover() {
  local attempt
  for attempt in 1 2 3; do
    adb mdns services 2>/dev/null |
      awk '$2 == "_adb-tls-connect._tcp" { print $3; exit }' | grep . && return 0
    sleep 1
  done
  return 1
}

# adb connect exits 0 even when it fails, so the outcome has to be read off its output.
connect_to() {
  local target="$1" output
  echo "==> Connecting to $target"
  output="$(adb connect "$target" 2>&1)"
  echo "$output"
  [[ "$output" != *"failed to connect"* ]]
}

target="${1:-}"

if [[ -z "$target" ]] && ! has_device; then
  echo "==> No device connected; discovering over mDNS"
  target="$(discover || true)"
  [[ -n "$target" ]] && echo "==> Found $target"
fi

if [[ -n "$target" ]]; then
  [[ "$target" == *:* ]] || target="${DEFAULT_IP}:${target}"
  connect_to "$target" || true
fi

# Bail before the build: an APK takes about a minute, and installing it is the whole point.
if ! has_device; then
  cat >&2 <<'MSG'
==> No device connected.

Check that Wireless Debugging is on. If it is, and connecting still fails, this machine
has lost its pairing with the phone — pairing is separate from connecting, and the phone
can revoke it. Open "Pair device with pairing code" on the phone (it shows a DIFFERENT
port than the Wireless Debugging screen) and run:

    adb pair <ip>:<pairing-port> <code>

Then run this script again.
MSG
  exit 1
fi

echo "==> Devices"
adb devices -l

echo "==> Installing debug APK ($VARIANT)"
./gradlew ":app:$VARIANT"

# Pick the first connected device's transport so install-on-2-transports doesn't make
# the launch ambiguous.
tid="$(adb devices | awk 'NR>1 && $2=="device"{print; exit}' | awk '{print $1}')"
echo "==> Launching $PKG on $tid"
adb -s "$tid" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "==> Done. App launched."
