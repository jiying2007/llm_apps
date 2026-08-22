#include "jingdu/core_api.h"

#include <atomic>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <thread>
#include <vector>

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

bool write_large_fixture(const char* path) {
  std::ofstream out(path, std::ios::binary | std::ios::trunc);
  if (!out) return false;
  const std::string block = "第十二章 压力测试\nabcdefghijklmnopqrstuvwxyz 世界 0123456789\n";
  const size_t target = 32U * 1024U * 1024U;
  size_t written = 0;
  while (written + block.size() < target) {
    out.write(block.data(), static_cast<std::streamsize>(block.size()));
    if (!out) return false;
    written += block.size();
  }
  out << "CROSS_BOUNDARY_SENTINEL_世界_END\n";
  return static_cast<bool>(out);
}

int malformed_input_is_rejected() {
  const char* paths[] = {"jingdu-overlong.txt", "jingdu-truncated.txt", "jingdu-surrogate.txt"};
  const std::vector<std::vector<unsigned char>> cases = {
      {0xC0, 0xAF},
      {'o', 'k', 0xE4, 0xB8},
      {0xED, 0xA0, 0x80}
  };
  for (size_t i = 0; i < cases.size(); ++i) {
    {
      std::ofstream out(paths[i], std::ios::binary | std::ios::trunc);
      CHECK(static_cast<bool>(out));
      out.write(reinterpret_cast<const char*>(cases[i].data()),
                static_cast<std::streamsize>(cases[i].size()));
      CHECK(static_cast<bool>(out));
    }
    jd_handle handle = 0;
    CHECK(jd_open_utf8(paths[i], &handle) == JD_EUTF8);
    CHECK(handle == 0);
    std::remove(paths[i]);
  }
  return 0;
}

int detector_contract() {
  char encoding[32]{};
  const uint8_t bom8[] = {0xEF, 0xBB, 0xBF, 'a'};
  CHECK(jd_detect_encoding(bom8, sizeof(bom8), encoding, sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-8");

  const uint8_t bom16le[] = {0xFF, 0xFE, 0x61, 0x00};
  CHECK(jd_detect_encoding(bom16le, sizeof(bom16le), encoding, sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-16LE");

  const uint8_t legacy[] = {0xD6, 0xD0, 0xCE, 0xC4};
  CHECK(jd_detect_encoding(legacy, sizeof(legacy), encoding, sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "GB18030");
  return 0;
}
}

int main() {
  CHECK(detector_contract() == 0);
  CHECK(malformed_input_is_rejected() == 0);

  const char* path = "jingdu-stress.txt";
  CHECK(write_large_fixture(path));

  jd_handle handle = 0;
  int open_status = jd_open_utf8(path, &handle);
  if (open_status != JD_OK) {
    std::cerr << "large fixture open status=" << open_status << std::endl;
    std::remove(path);
    return 1;
  }
  CHECK(handle != 0);
  const uint64_t byte_count = jd_byte_count(handle);
  const uint64_t char_count = jd_char_count(handle);
  std::cerr << "large fixture bytes=" << byte_count << " chars=" << char_count << std::endl;
  CHECK(byte_count >= 31U * 1024U * 1024U);
  CHECK(char_count > 10U * 1024U * 1024U);

  jd_buffer result{};
  CHECK(jd_search(handle, "CROSS_BOUNDARY_SENTINEL_世界_END", 2, &result) == JD_OK);
  std::string hits = take(&result);
  CHECK(hits.find("CROSS_BOUNDARY_SENTINEL") != std::string::npos);

  uint64_t middle = char_count / 2;
  CHECK(jd_read(handle, middle, 4096, &result) == JD_OK);
  CHECK(!take(&result).empty());

  std::atomic<int> failures{0};
  std::vector<std::thread> workers;
  for (int worker_index = 0; worker_index < 8; ++worker_index) {
    workers.emplace_back([&, worker_index]() {
      for (int iteration = 0; iteration < 100; ++iteration) {
        uint64_t offset = (middle + static_cast<uint64_t>(worker_index * 997 + iteration * 113)) % char_count;
        jd_buffer local{};
        int status = jd_read(handle, offset, 512, &local);
        if (status != JD_OK || local.size == 0) ++failures;
        jd_buffer_free(&local);
      }
    });
  }
  for (auto& worker : workers) worker.join();
  CHECK(failures.load() == 0);

  for (int i = 0; i < 256; ++i) {
    jd_handle extra = 0;
    CHECK(jd_open_utf8(path, &extra) == JD_OK);
    CHECK(extra != 0);
    jd_close(extra);
  }

  jd_close(handle);
  std::remove(path);
  return 0;
}
