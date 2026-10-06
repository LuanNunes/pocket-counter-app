#!/usr/bin/env bash
# Print the SHA-1 (and SHA-256) of a signing keystore — the fingerprint you must
# register on the Android OAuth client in Google Cloud Console for Google sign-in.
#
# Usage:
#   ./print-sha1.sh                       # debug keystore (~/.android/debug.keystore)
#   ./print-sha1.sh <keystore> <alias>    # a custom/release keystore (prompts for passwords)
#
# After getting the SHA-1: APIs & Services → Credentials → Create credentials →
# OAuth client ID → Android, package com.resolveprogramming.pocketcounter.

set -euo pipefail

KEYSTORE="${1:-$HOME/.android/debug.keystore}"

if [[ $# -ge 2 ]]; then
  # Custom keystore: let keytool prompt for the store/key passwords.
  keytool -list -v -keystore "$KEYSTORE" -alias "$2" | grep -E "SHA1:|SHA256:"
else
  # Android debug keystore uses well-known fixed credentials.
  keytool -list -v -keystore "$KEYSTORE" -alias androiddebugkey \
    -storepass android -keypass android | grep -E "SHA1:|SHA256:"
fi
