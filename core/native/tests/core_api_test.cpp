#include "jingdu/core_api.h"
#include <cassert>
#include <cstdio>
#include <fstream>
#include <string>

static std::string take(jd_buffer* b) { std::string s(b->data ? b->data : "", static_cast<size_t>(b->size)); jd_buffer_free(b); return s; }
int main() {
  assert(jd_abi_version() == 1);
  char enc[32]{}; const uint8_t utf8[] = {'a','b','c'};
  assert(jd_detect_encoding(utf8, 3, enc, sizeof(enc)) == JD_OK); assert(std::string(enc) == "UTF-8");
  const char* path = "jingdu-core-test.txt";
  { std::ofstream f(path, std::ios::binary); f << "第一章 开始\nhello 世界\nChapter 2 Next\nhello again\n"; }
  jd_handle h = 0; assert(jd_open_utf8(path, &h) == JD_OK); assert(h != 0); assert(jd_char_count(h) > 20);
  jd_buffer b{}; assert(jd_read(h, 0, 6, &b) == JD_OK); assert(!take(&b).empty());
  assert(jd_search(h, "hello", 10, &b) == JD_OK); assert(take(&b).find("hello") != std::string::npos);
  assert(jd_chapters(h, 100, &b) == JD_OK); std::string chapters = take(&b); assert(chapters.find("第一章") != std::string::npos); assert(chapters.find("Chapter 2") != std::string::npos);
  assert(jd_speech_chunk(h, 0, 20, &b) == JD_OK); assert(take(&b).find('\t') != std::string::npos);
  const char* out = "jingdu-core-repaired.txt"; assert(jd_export_rules(h, "hello\x1fhi", out) == JD_OK);
  std::ifstream repaired(out, std::ios::binary); std::string content((std::istreambuf_iterator<char>(repaired)), std::istreambuf_iterator<char>());
  assert(content.find("hi 世界") != std::string::npos); assert(content.find("hello") == std::string::npos);
  jd_close(h); std::remove(path); std::remove(out); return 0;
}
