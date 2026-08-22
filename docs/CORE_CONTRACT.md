# Shared Core Contract

`core/native` is the only implementation of document business and algorithm semantics. Android and HarmonyOS are platform shells; they may adapt files, threads, lifecycle, UI, TTS and OS storage APIs, but they must not reimplement core document behavior.

## ABI

The public boundary is `core/native/include/jingdu/core_api.h`. ABI v2 is a C ABI so both JNI and Node-API can depend on a stable language-neutral contract.

Rules:

- ABI breaking changes increment `jd_abi_version()` and update Android JNI, Harmony Node-API, tests and this document in the same change.
- No STL types, exceptions, platform objects or ownership-bearing C++ objects cross the ABI.
- Strings are UTF-8. Paths are UTF-8 process-local paths to app-private files.
- `jd_handle` is process-local, opaque and must be closed exactly once with `jd_close`.
- Buffers returned in `jd_buffer` are owned by the caller and must be released with `jd_buffer_free`.
- Returned buffers remain valid until freed; other core calls do not invalidate them.
- Read/search/chapter/speech offsets are Unicode scalar/code-point offsets, never UTF-8 byte offsets or UTF-16 code-unit offsets.
- APIs are synchronous. Platform shells must move unbounded work off their UI thread.

## Thread safety

Different handles and read-only operations on the same handle may be called concurrently. The handle registry is internally synchronized. A handle must not be closed while another thread is using it. Platform lifecycle code owns that exclusion.

## Limits

- sparse index stride: implementation detail, currently 4096 code points;
- one bounded read: at most 1,048,576 code points;
- search results: at most 10,000;
- chapter results: at most 20,000;
- one TTS chunk request: at most 4,000 code points.

These limits are defensive product contracts and may only be increased with performance evidence.

## Errors

`JD_OK` is success. All other `jd_status` values are stable ABI values. Platform bridges translate failures to platform-native errors without changing their meaning. Invalid UTF-8 in a normalized document is a hard `JD_EUTF8` failure rather than silent replacement.

## Derived revisions

`jd_repair_revision(normalizedSha256, rulePack)` is deterministic. A clean/repair artifact is valid only for exactly that normalized document hash and rule pack. Platform-specific timestamps must never be used as document or revision identity.
