#ifndef JINGDU_CORE_API_H
#define JINGDU_CORE_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef uint64_t jd_handle;
typedef struct jd_buffer { char* data; uint64_t size; } jd_buffer;

enum jd_status {
  JD_OK = 0,
  JD_EINVAL = 1,
  JD_ENOENT = 2,
  JD_EIO = 3,
  JD_EUTF8 = 4,
  JD_ENOMEM = 5,
  JD_EHANDLE = 6
};

uint32_t jd_abi_version(void);
const char* jd_core_version(void);
int jd_detect_encoding(const uint8_t* data, uint64_t size, char* out_name, uint64_t out_capacity);
int jd_open_utf8(const char* path, jd_handle* out_handle);
void jd_close(jd_handle handle);
uint64_t jd_char_count(jd_handle handle);
uint64_t jd_byte_count(jd_handle handle);
int jd_read(jd_handle handle, uint64_t char_offset, uint64_t max_chars, jd_buffer* out);
int jd_search(jd_handle handle, const char* utf8_query, uint32_t limit, jd_buffer* out);
int jd_chapters(jd_handle handle, uint32_t limit, jd_buffer* out);
int jd_speech_chunk(jd_handle handle, uint64_t char_offset, uint64_t max_chars, jd_buffer* out);
int jd_export_rules(jd_handle handle, const char* packed_rules, const char* output_path);
void jd_buffer_free(jd_buffer* buffer);

#ifdef __cplusplus
}
#endif
#endif
