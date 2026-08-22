# Encoding Contract

Import samples at most 64 KiB of the private source copy and tells the shared core whether the sample is truncated.

AUTO detection order:

1. UTF-8 BOM;
2. UTF-16LE/BE BOM;
3. no-BOM UTF-16 zero-byte pattern;
4. strict UTF-8, allowing only an incomplete final multibyte sequence when the sample-truncated flag is set;
5. Big5 versus GB18030 validation/Chinese-frequency heuristic;
6. conservative GB18030 fallback.

Manual override remains available for UTF-8, GB18030, GBK, GB2312, Big5, UTF-16LE and UTF-16BE (Android also accepts the platform `UTF-16` alias).

Platform decoding uses the operating system charset implementation and writes normalized UTF-8 to app-private storage. Malformed bytes under an explicit/fallback legacy charset use replacement semantics so a user can still open damaged text; malformed UTF-8 after normalization is rejected by the shared core.

The 64 KiB boundary is part of the contract: an otherwise valid UTF-8 file must not become GB18030 merely because the sample ends inside a 2/3/4-byte character.
