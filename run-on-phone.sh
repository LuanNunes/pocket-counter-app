#!/usr/bin/env bash
# Connect to the phone over Wireless Debugging, install the debug APK, and launch it.
#
# Usage:
#   ./run-on-phone.sh <port>        # connect to 192.168.50.128:<port>, install, launch
#   ./run-on-phone.sh <ip>:<port>   # use a different IP
#   ./run-on-phone.sh               # skip connect, just install + launch (already connected)
#
# Pairing is permanent — you only need to re-connect. Read the current port off the
# phone's "Wireless debugging" screen (it changes when you toggle it or reboot).

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

if [[ $# -ge 1 ]]; then
  target="$1"
  [[ "$target" == *:* ]] || target="${DEFAULT_IP}:${target}"
  echo "==> Connecting to $target"
  adb connect "$target"
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
