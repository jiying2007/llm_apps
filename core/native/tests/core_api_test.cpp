#include "jingdu/core_api.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>

namespace {
[[noreturn]] void fail(const char* message) {
  std::cerr << "FAIL: " << message << '\n';
  std::exit(1);
}

void check(bool condition, const char* message) {
  if (!condition) fail(message);
}

std::string take(jd_buffer* buffer) {
  std::string value(buffer->data ? buffer->data : "", static_cast<size_t>(buffer->size));
  jd_buffer_free(buffer);
  return value;
}

std::string detect(const uint8_t* data, size_t size, uint32_t flags = 0) {
  char name[32]{};
  check(jd_detect_encoding(data, size, flags, name, sizeof(name)) == JD_OK,
        "encoding detection status");
  return name;
}
}  // namespace

int main() {
  check(jd_abi_version() == 2, "ABI version");

  const uint8_t ascii[] = {'a', 'b', 'c'};
  check(detect(ascii, sizeof(ascii)) == "UTF-8", "ASCII is UTF-8");

  char hash[JD_SHA256_HEX_SIZE]{};
  check(jd_sha256(ascii, sizeof(ascii), hash, sizeof(hash)) == JD_OK, "SHA status");
  check(std::string(hash) ==
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "SHA known vector");

  const uint8_t utf16le[] = {'a', 0, 'b', 0, 'c', 0, 'd', 0, 'e', 0};
  check(detect(utf16le, sizeof(utf16le)) == "UTF-16LE", "UTF-16LE zero pattern");

  const uint8_t truncatedUtf8[] = {'x', 'y', 0xE4, 0xB8};
  check(detect(truncatedUtf8, sizeof(truncatedUtf8), JD_DETECT_SAMPLE_TRUNCATED) ==
            "UTF-8",
        "truncated UTF-8 sample boundary");
  check(detect(truncatedUtf8, sizeof(truncatedUtf8), JD_DETECT_NONE) != "UTF-8",
        "non-truncated malformed UTF-8 rejected");

  const uint8_t big5[] = {0xA4, 0x40, 0xA4, 0xA4, 0xA4, 0xE5, 0xA4, 0x40};
  check(detect(big5, sizeof(big5)) == "Big5", "Big5 strong evidence");
  const uint8_t gb[] = {0xD2, 0xBB, 0xCA, 0xC7, 0xD6, 0xD0, 0xCE, 0xC4};
  check(detect(gb, sizeof(gb)) == "GB18030", "GB18030 fallback");

  const char* path = "jingdu-core-test.txt";
  {
    std::ofstream file(path, std::ios::binary);
    file << "第一章 开始\nhello 世界\nChapter 2 Next\nhello again\n";
  }
  check(jd_file_sha256(path, hash, sizeof(hash)) == JD_OK, "file SHA status");
  check(std::string(hash).size() == 64, "file SHA length");

  char revision1[JD_SHA256_HEX_SIZE]{};
  char revision2[JD_SHA256_HEX_SIZE]{};
  char revision3[JD_SHA256_HEX_SIZE]{};
  check(jd_repair_revision(hash, "hello\x1f" "hi", revision1, sizeof(revision1)) == JD_OK,
        "revision status");
  check(jd_repair_revision(hash, "hello\x1f" "hi", revision2, sizeof(revision2)) == JD_OK,
        "revision repeat status");
  check(jd_repair_revision(hash, "hello\x1f" "bye", revision3, sizeof(revision3)) == JD_OK,
        "revision changed-rule status");
  check(std::string(revision1) == revision2, "revision deterministic");
  check(std::string(revision1) != revision3, "revision changes with rule pack");

  jd_buffer invalidBuffer{};
  check(jd_read(0, 0, 10, &invalidBuffer) == JD_EHANDLE, "invalid handle read status");
  check(jd_search(0, "hello", 10, &invalidBuffer) == JD_EHANDLE,
        "invalid handle search status");

  jd_handle handle = 0;
  check(jd_open_utf8(path, &handle) == JD_OK && handle != 0, "open");
  check(jd_char_count(handle) > 20, "char count");

  jd_buffer buffer{};
  check(jd_read(handle, 0, 6, &buffer) == JD_OK && !take(&buffer).empty(), "read");
  check(jd_search(handle, "hello", 10, &buffer) == JD_OK &&
            take(&buffer).find("hello") != std::string::npos,
        "search");
  check(jd_chapters(handle, 100, &buffer) == JD_OK, "chapters status");
  const std::string chapters = take(&buffer);
  check(chapters.find("第一章") != std::string::npos &&
            chapters.find("Chapter 2") != std::string::npos,
        "chapters content");
  check(jd_speech_chunk(handle, 0, 20, &buffer) == JD_OK &&
            take(&buffer).find('\t') != std::string::npos,
        "speech chunk");

  const char* repairedPath = "jingdu-core-repaired.txt";
  check(jd_export_rules(handle, "hello\x1f" "hi", repairedPath) == JD_OK,
        "repair export");
  std::ifstream repaired(repairedPath, std::ios::binary);
  const std::string content((std::istreambuf_iterator<char>(repaired)),
                            std::istreambuf_iterator<char>());
  check(content.find("hi 世界") != std::string::npos &&
            content.find("hello") == std::string::npos,
        "repair content");

  jd_close(handle);
  std::remove(path);
  std::remove(repairedPath);
  return 0;
}
