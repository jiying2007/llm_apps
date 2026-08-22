#ifndef JINGDU_CORE_API_H
#define JINGDU_CORE_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JD_SHA256_HEX_SIZE 65U

typedef uint64_t jd_handle;
typedef uint32_t jd_status;
typedef uint32_t jd_detect_flags;

#define JD_OK ((jd_status)0U)
#define JD_EINVAL ((jd_status)1U)
#define JD_ENOENT ((jd_status)2U)
#define JD_EIO ((jd_status)3U)
#define JD_EUTF8 ((jd_status)4U)
#define JD_ENOMEM ((jd_status)5U)
#define JD_EHANDLE ((jd_status)6U)

#define JD_DETECT_NONE ((jd_detect_flags)0U)
#define JD_DETECT_SAMPLE_TRUNCATED ((jd_detect_flags)(1U << 0))

typedef struct jd_buffer {
  char* data;
  uint64_t size;
} jd_buffer;

uint32_t jd_abi_version(void);
const char* jd_core_version(void);

/*
 * Detects the source encoding from a bounded sample.
 * Set JD_DETECT_SAMPLE_TRUNCATED when more source bytes exist after `data`.
 * That flag allows an incomplete final multibyte sequence to be ignored rather
 * than incorrectly classifying an otherwise valid UTF-8/legacy sample.
 */
int jd_detect_encoding(const uint8_t* data, uint64_t size, uint32_t flags,
                       char* out_name, uint64_t out_capacity);

/* Lowercase SHA-256 hex, always 64 chars plus NUL. */
int jd_sha256(const uint8_t* data, uint64_t size, char* out_hex,
              uint64_t out_capacity);
int jd_file_sha256(const char* path, char* out_hex, uint64_t out_capacity);

/* Deterministic derived-view revision: SHA256(normalizedSha + rule pack). */
int jd_repair_revision(const char* normalized_sha256, const char* packed_rules,
                       char* out_hex, uint64_t out_capacity);

/* Opens a normalized UTF-8 document. Handles are process-local. */
int jd_open_utf8(const char* path, jd_handle* out_handle);
void jd_close(jd_handle handle);
uint64_t jd_char_count(jd_handle handle);
uint64_t jd_byte_count(jd_handle handle);

/* All offsets/counts below are Unicode scalar/code-point counts, not bytes. */
int jd_read(jd_handle handle, uint64_t char_offset, uint64_t max_chars,
            jd_buffer* out);
int jd_search(jd_handle handle, const char* utf8_query, uint32_t limit,
              jd_buffer* out);
int jd_chapters(jd_handle handle, uint32_t limit, jd_buffer* out);
int jd_speech_chunk(jd_handle handle, uint64_t char_offset,
                    uint64_t max_chars, jd_buffer* out);
int jd_export_rules(jd_handle handle, const char* packed_rules,
                    const char* output_path);

/* Frees only buffers returned by this ABI. Safe on NULL/empty buffers. */
void jd_buffer_free(jd_buffer* buffer);

#ifdef __cplusplus
}
#endif
#endif
