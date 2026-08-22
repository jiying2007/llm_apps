# Jingdu Shared Core

`core/native` is the only production business/algorithm core for Android and HarmonyOS.

Boundary:
- platform import adapters decode user-selected legacy encodings to normalized UTF-8 without modifying the source file;
- this core owns UTF-8 validation/indexing, bounded window reads, full-text search, chapter discovery, literal repair export, and speech segmentation;
- Android calls it through JNI; HarmonyOS calls the same ABI through Node-API;
- no Java/Kotlin/ArkTS business-core fallback exists.

The C ABI in `include/jingdu/core_api.h` is the cross-platform contract. Breaking it requires an ABI version bump and both platform bridges in the same change.
