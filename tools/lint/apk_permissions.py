#!/usr/bin/env python3
"""Nothing leaves the phone (README, constraint 1): the built APK must not ask for network access.

Reads the permissions of the debug APK with aapt2 and fails on INTERNET, or on any permission not
in ALLOWED, so a new dependency cannot add one unnoticed.
"""
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
APK = ROOT / "androidApp/build/outputs/apk/debug/androidApp-debug.apk"

# Reviewed: the evening reminder (POST_NOTIFICATIONS) and what WorkManager needs to schedule it
# (WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE; ACCESS_NETWORK_STATE only reads whether a
# network is up, for work constraints this app does not use). None of them can send data.
ALLOWED = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.ACCESS_NETWORK_STATE",
    "dev.valkhes.mealplanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}


def aapt2() -> pathlib.Path:
    sdk = pathlib.Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "/opt/android-sdk")
    tools = sorted((sdk / "build-tools").glob("*/aapt2"))
    if not tools:
        sys.exit(f"apk_permissions: no aapt2 under {sdk}/build-tools")
    return tools[-1]


def main() -> int:
    if not APK.exists():
        print(f"apk_permissions: {APK.relative_to(ROOT)} not built", file=sys.stderr)
        return 1
    out = subprocess.run([str(aapt2()), "dump", "permissions", str(APK)], capture_output=True, text=True, check=True).stdout
    used = {line.split("name='")[1].split("'")[0] for line in out.splitlines() if line.startswith("uses-permission:")}
    errors = sorted(used - ALLOWED)
    for p in errors:
        print(f"apk_permissions: the APK asks for {p}: not allowed (README, constraint 1)", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
