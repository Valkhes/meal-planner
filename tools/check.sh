#!/usr/bin/env bash
# Checks everything, the same way locally and in CI (.github/workflows/checks.yml):
# translations and doc references (seconds, no Gradle), then ktlint, every JVM unit test (domain,
# application, infrastructure, architecture rules, the Android app's sync crypto and sockets), Android
# lint, the debug APK, and the APK's permissions. Run it after any change, before saying a task is done.
set -euo pipefail
cd "$(dirname "$0")/.."

python3 tools/lint/translations.py
python3 tools/lint/doc_refs.py

if [[ -z "${ANDROID_HOME:-}" && -d /opt/android-sdk ]]; then
  export ANDROID_HOME=/opt/android-sdk
fi

./gradlew --console=plain ktlintCheck jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug

python3 tools/lint/apk_permissions.py
echo "check: all good"
