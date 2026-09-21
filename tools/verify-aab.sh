#!/usr/bin/env bash
# Confirm an Android App Bundle was actually signed, and report which certificate signed it.
#
# Unlike the APK check this does NOT pin the fingerprint. With Play App Signing the bundle is
# signed with the *upload* key, which is allowed to differ from the app signing key Google uses
# for what users download, and the upload key may legitimately be rotated. So: fail if unsigned,
# report otherwise, and leave the matching to Play.
set -euo pipefail

AAB="${1:?usage: verify-aab.sh <path-to-aab>}"
KEYTOOL="${JAVA_HOME:-/usr}/bin/keytool"

fail() { echo "verify-aab: FAIL: $*" >&2; exit 1; }

[[ -f "$AAB" ]] || fail "no such bundle: $AAB"
[[ -x "$KEYTOOL" ]] || fail "keytool not found at $KEYTOOL"

# An unsigned bundle has no signature block in META-INF at all.
if ! unzip -l "$AAB" | grep -qE "META-INF/.*\.(RSA|DSA|EC)$"; then
  fail "bundle contains no signature block — it was built without a signing key"
fi

FINGERPRINT="$("$KEYTOOL" -printcert -jarfile "$AAB" 2>/dev/null \
  | grep -i "SHA256:" | head -1 | sed 's/.*SHA256: *//' | tr -d ': ' | tr 'A-Z' 'a-z')"
[[ -n "$FINGERPRINT" ]] || fail "could not read a certificate from the bundle"

echo "verify-aab: OK"
echo "  file        $(basename "$AAB")"
echo "  upload cert $FINGERPRINT"
