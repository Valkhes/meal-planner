#!/usr/bin/env bash
# Claude Code SessionStart hook (.claude/settings.json): every session commits as the owner
# (CLAUDE.md, Commits), and Gradle finds the Android SDK when the machine has one in /opt.
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
git config user.name "Valkhes"
git config user.email "20518397+Valkhes@users.noreply.github.com"
if [[ ! -f local.properties && -d /opt/android-sdk ]]; then
  echo "sdk.dir=/opt/android-sdk" > local.properties
fi
exit 0
