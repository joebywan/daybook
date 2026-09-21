#!/usr/bin/env bash
# Gate a release artifact before it is published.
#
# Three independent things have shipped broken before and each is checked separately here:
#   1. the APK is signed at all (a green Gradle build is not proof of this)
#   2. it is signed with THE key, not a new one, which would break every in-place update
#   3. it is not debuggable, which is what blocks updates once Play Protect gets involved
set -euo pipefail

APK="${1:?usage: verify-apk.sh <path-to-apk>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_FILE="$ROOT/android/release-key.sha256"

fail() { echo "verify-apk: FAIL: $*" >&2; exit 1; }

[[ -f "$APK" ]] || fail "no such APK: $APK"

# 1. Signed at all. AGP names the artifact *-unsigned.apk when it had no key.
case "$(basename "$APK")" in
  *unsigned*) fail "artifact is named $(basename "$APK") — it was built without a signing key" ;;
esac

# 2. Signed with the expected certificate.
[[ -f "$EXPECTED_FILE" ]] || fail "missing $EXPECTED_FILE"
EXPECTED="$(tr -d '[:space:]' < "$EXPECTED_FILE")"
ACTUAL="$(python3 "$ROOT/tools/apk-cert.py" "$APK")" || fail "could not read the signing certificate"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  fail "certificate mismatch.
  expected $EXPECTED
  actual   $ACTUAL
  Publishing this would break in-place updates for everyone who already has the app."
fi

# 3. Not debuggable.
AAPT2="$(ls -1 "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
[[ -n "$AAPT2" ]] || fail "aapt2 not found under ANDROID_HOME; cannot prove the APK is not debuggable"
if "$AAPT2" dump badging "$APK" 2>/dev/null | grep -q "application-debuggable"; then
  fail "APK declares android:debuggable — this is what blocks in-place updates"
fi

echo "verify-apk: OK"
echo "  file        $(basename "$APK")"
echo "  certificate $ACTUAL"
echo "  debuggable  no"
