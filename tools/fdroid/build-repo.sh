#!/usr/bin/env bash
# Adds the signed release APKs in $1/repo to an F-Droid repository and (re)writes its signed index.
# usage: build-repo.sh <dir>   with <dir>/repo/*.apk already in place.
# Needs `pip install fdroidserver`, ANDROID_HOME (apksigner), and in the environment the keystore
# (FDROID_KEYSTORE, FDROID_KEY_ALIAS, FDROID_KEY_STORE_PASS, FDROID_KEY_PASS). The index is signed
# with whatever key that is; the workflow passes the release key, so the repo fingerprint is the app's.
set -euo pipefail
DIR="$(cd "${1:?usage: build-repo.sh <dir>}" && pwd)"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PKG=com.joebywan.daybook
KEEP=5   # newest APKs kept; Pages has a 1 GB cap and nobody installs a build from last month

cd "$DIR"
# Older APKs go before `fdroid update`, so the index never lists them.
ls repo/*.apk | sort -V | head -n "-$KEEP" | xargs -r rm -v

mkdir -p "metadata/$PKG"
cp "$HERE/tools/fdroid/$PKG.yml" metadata/
# The same listing text and images the official F-Droid build reads straight from the repo.
cp -r "$HERE/fastlane/metadata/android/en-US" "metadata/$PKG/"

install -m 600 /dev/null config.yml
cat > config.yml <<CFG
repo_url: https://knowhowit.com.au/daybook/fdroid/repo
repo_name: Daybook
repo_description: Daybook, the daily logic-puzzle app, straight from its releases.
repo_icon: icon.png
keystore: $FDROID_KEYSTORE
repo_keyalias: $FDROID_KEY_ALIAS
keystorepass: {env: FDROID_KEY_STORE_PASS}
keypass: {env: FDROID_KEY_PASS}
keydname: CN=Daybook, O=Daybook
make_current_version_link: false
CFG
cp "$HERE/android/play-icon-512.png" icon.png
fdroid update
