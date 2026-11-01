#!/usr/bin/env bash
# Neue Version bauen, signieren und als GitHub-Release veröffentlichen.
# Aufruf: scripts/release.sh 1.1
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:?Version angeben, z. B. scripts/release.sh 1.1}"
TAG="v$NAME"

[[ -f keystore.properties ]] || { echo "keystore.properties fehlt – ohne den Release-Schlüssel sind keine Updates möglich."; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Erst alle Änderungen committen."; exit 1; }
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "Tag $TAG gibt es schon."; exit 1; }

NOTES=$(awk -v v="## $NAME" '$0==v{f=1;next} /^## /{f=0} f' CHANGELOG.md)
[[ -n "${NOTES//[[:space:]]/}" ]] || { echo "In CHANGELOG.md fehlt der Abschnitt „## $NAME“."; exit 1; }

CODE=$(( $(grep '^VERSION_CODE=' version.properties | cut -d= -f2) + 1 ))
printf 'VERSION_CODE=%s\nVERSION_NAME=%s\n' "$CODE" "$NAME" > version.properties

./gradlew --no-daemon -q assembleRelease

mkdir -p release
APK="release/Yolcu-$NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
(cd release && sha256sum "Yolcu-$NAME.apk" > "Yolcu-$NAME.apk.sha256")

git commit -q -am "Version $NAME"
git tag -a "$TAG" -m "Yolcu $NAME"
git push -q
git push -q origin "$TAG"

if command -v gh >/dev/null; then
    gh release create "$TAG" "$APK" "$APK.sha256" --title "Yolcu $NAME" --notes "$NOTES"
    echo "Release $TAG veröffentlicht."
else
    echo "gh nicht installiert – Release $TAG auf GitHub von Hand anlegen und $APK + .sha256 hochladen."
fi
