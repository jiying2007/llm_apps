#!/usr/bin/env bash
set -euo pipefail

for path in prototype android-prototype txt_ref_apps research apps/android/core core/src apps/android/dist docs/txt-reader; do
  test ! -e "$path" || { echo "legacy path remains: $path" >&2; exit 1; }
done

if find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.hap' -o -name '*.hsp' -o -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.p7b' \) -not -path './.git/*' | grep .; then
  echo 'committed binary/signing artifact found' >&2
  exit 1
fi

if git grep -n -E 'com\.jingdu\.txt\.w0|android-prototype|prototype/core|device-pending|console-pending' -- ':!scripts/verify-terminal.sh'; then
  echo 'legacy implementation reference found' >&2
  exit 1
fi

if git grep -n -E 'implementation project\(":core"\)|include\(":app", ":core"\)' -- apps/android; then
  echo 'Android Java shared core dependency found' >&2
  exit 1
fi

test -f core/native/include/jingdu/core_api.h
test -f core/native/src/core_api.cpp
test -f apps/android/app/src/main/cpp/native_bridge.cpp
test -f apps/harmony/entry/src/main/cpp/napi_init.cpp
test -f apps/harmony/entry/src/main/ets/pages/Index.ets
test -f apps/harmony/entry/src/main/ets/model/BookStore.ets
test -f apps/harmony/entry/src/main/ets/model/TtsController.ets
test ! -f apps/android/app/src/main/java/com/junchen/jingdu/ReaderSurfaceView.java
