#!/usr/bin/env sh
# Bootstrap shim for environments where Gradle is installed by CI.
# The GitHub Actions workflow generates the official Gradle Wrapper before building.
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Run the GitHub Actions workflow to build the APK." >&2
exit 127
