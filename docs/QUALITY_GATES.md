# Quality gates

A change is mergeable only when all applicable gates pass.

1. Native core: host CMake release build + CTest.
2. Android: debug/release lint, debug APK, release AAB, native bridge compilation.
3. HarmonyOS: DevEco/Hvigor build with HarmonyOS SDK 6.0+; native Node-API bridge must link `core/native` rather than a fork.
4. Repository contract: no legacy roots, no Java shared core, no transition markers, no committed APK/AAB/HAP/keystore artifacts.
5. Device gate before store release: source import, legacy encoding, reopen/position restore, search, chapters, bookmarks, literal repair, clean export, auto reading and TTS on both platforms.
6. Store gate: final identity/version/signing/privacy/listing and rollback artifacts are verified outside source control.

A passing source CI does not replace device/store evidence; likewise device evidence does not allow bypassing source CI.
