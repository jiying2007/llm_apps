# Android

Native Android shell for Jingdu TXT. Document behavior is provided by the single shared C++ core through JNI. Android owns only file selection/charset normalization, UI, lifecycle, audio focus and system text-to-speech integration.

Build with `./gradlew --no-daemon androidCheck`.
