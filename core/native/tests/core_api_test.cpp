#include "jingdu/core_api.h"

#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>

namespace {
int fail(const char* expression, int line) {
  std::cerr << "CHECK failed at line " << line << ": " << expression << std::endl;
  return 1;
}
#define CHECK(expr) do { if (!(expr)) return fail(#expr, __LINE__); } while (0)

std::string take(jd_buffer* buffer) {
  std::string value(buffer->data ? buffer->data : "", static_cast<size_t>(buffer->size));
  jd_buffer_free(buffer);
  return value;
}
}

int main() {
  CHECK(jd_abi_version() == 1);

  char encoding[32]{};
  const uint8_t utf8[] = {'a', 'b', 'c'};
  CHECK(jd_detect_encoding(utf8, 3, encoding, sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-8");

  const char* path = "jingdu-core-test.txt";
  {
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    CHECK(static_cast<bool>(file));
    file << "第一章 开始\nhello 世界\nChapter 2 Next\nhello again\n";
  }

  jd_handle handle = 0;
  CHECK(jd_open_utf8(path, &handle) == JD_OK);
  CHECK(handle != 0);
  CHECK(jd_char_count(handle) > 20);

  jd_buffer buffer{};
  CHECK(jd_read(handle, 0, 6, &buffer) == JD_OK);
  CHECK(!take(&buffer).empty());

  CHECK(jd_search(handle, "hello", 10, &buffer) == JD_OK);
  CHECK(take(&buffer).find("hello") != std::string::npos);

  CHECK(jd_chapters(handle, 100, &buffer) == JD_OK);
  std::string chapters = take(&buffer);
  CHECK(chapters.find("第一章") != std::string::npos);
  CHECK(chapters.find("Chapter 2") != std::string::npos);

  CHECK(jd_speech_chunk(handle, 0, 20, &buffer) == JD_OK);
  CHECK(take(&buffer).find('\t') != std::string::npos);

  const char* output = "jingdu-core-repaired.txt";
  CHECK(jd_export_rules(handle, "hello\x1fhi", output) == JD_OK);
  std::ifstream repaired(output, std::ios::binary);
  CHECK(static_cast<bool>(repaired));
  std::string content((std::istreambuf_iterator<char>(repaired)), std::istreambuf_iterator<char>());
  CHECK(content.find("hi 世界") != std::string::npos);
  CHECK(content.find("hello") == std::string::npos);

  jd_close(handle);
  std::remove(path);
  std::remove(output);
  return 0;
}
