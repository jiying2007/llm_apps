#!/usr/bin/env bash
set -euo pipefail
for path in prototype android-prototype txt_ref_apps research apps/android/core core/src apps/android/dist docs/txt-reader; do
  test ! -e "$path" || { echo "legacy path remains: $path" >&2; exit 1; }
done
if find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.jks' -o -name '*.keystore' \) -not -path './.git/*' | grep .; then
  echo 'committed binary/signing artifact found' >&2; exit 1
fi
if grep -R -nE 'W0|device-pending|console-pending|android-prototype|prototype/core' README.md core apps docs .github 2>/dev/null; then
  echo 'legacy transition marker found' >&2; exit 1
fi
test -f core/native/include/jingdu/core_api.h
test -f apps/android/app/src/main/cpp/native_bridge.cpp
test -f apps/harmony/entry/src/main/cpp/napi_init.cpp
test ! -f apps/android/app/src/main/java/com/junchen/jingdu/ReaderSurfaceView.java
