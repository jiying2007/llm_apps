#include "jingdu/core_api.h"

#include <atomic>
#include <cassert>
#include <cstdio>
#include <fstream>
#include <string>
#include <thread>
#include <vector>

namespace {
std::string take(jd_buffer* buffer) {
  std::string value(buffer->data ? buffer->data : "", static_cast<size_t>(buffer->size));
  jd_buffer_free(buffer);
  return value;
}

void write_large_fixture(const char* path) {
  std::ofstream out(path, std::ios::binary | std::ios::trunc);
  assert(out);
  const std::string block = "第十二章 压力测试\nabcdefghijklmnopqrstuvwxyz 世界 0123456789\n";
  const size_t target = 32U * 1024U * 1024U;
  size_t written = 0;
  while (written + block.size() < target) {
    out.write(block.data(), static_cast<std::streamsize>(block.size()));
    written += block.size();
  }
  out << "CROSS_BOUNDARY_SENTINEL_世界_END\n";
}

void malformed_input_is_rejected() {
  const char* paths[] = {"jingdu-overlong.txt", "jingdu-truncated.txt", "jingdu-surrogate.txt"};
  const std::vector<std::vector<unsigned char>> cases = {
      {0xC0, 0xAF},
      {'o', 'k', 0xE4, 0xB8},
      {0xED, 0xA0, 0x80}
  };
  for (size_t i = 0; i < cases.size(); ++i) {
    {
      std::ofstream out(paths[i], std::ios::binary | std::ios::trunc);
      out.write(reinterpret_cast<const char*>(cases[i].data()),
                static_cast<std::streamsize>(cases[i].size()));
    }
    jd_handle handle = 0;
    assert(jd_open_utf8(paths[i], &handle) == JD_EUTF8);
    assert(handle == 0);
    std::remove(paths[i]);
  }
}

void detector_contract() {
  char encoding[32]{};
  const uint8_t bom8[] = {0xEF, 0xBB, 0xBF, 'a'};
  assert(jd_detect_encoding(bom8, sizeof(bom8), encoding, sizeof(encoding)) == JD_OK);
  assert(std::string(encoding) == "UTF-8");
  const uint8_t bom16le[] = {0xFF, 0xFE, 0x61, 0x00};
  assert(jd_detect_encoding(bom16le, sizeof(bom16le), encoding, sizeof(encoding)) == JD_OK);
  assert(std::string(encoding) == "UTF-16LE");
  const uint8_t legacy[] = {0xD6, 0xD0, 0xCE, 0xC4};
  assert(jd_detect_encoding(legacy, sizeof(legacy), encoding, sizeof(encoding)) == JD_OK);
  assert(std::string(encoding) == "GB18030");
}
}

int main() {
  detector_contract();
  malformed_input_is_rejected();

  const char* path = "jingdu-stress.txt";
  write_large_fixture(path);
  jd_handle handle = 0;
  assert(jd_open_utf8(path, &handle) == JD_OK);
  assert(handle != 0);
  assert(jd_byte_count(handle) >= 31U * 1024U * 1024U);
  assert(jd_char_count(handle) > 10U * 1024U * 1024U);

  jd_buffer result{};
  assert(jd_search(handle, "CROSS_BOUNDARY_SENTINEL_世界_END", 2, &result) == JD_OK);
  std::string hits = take(&result);
  assert(hits.find("CROSS_BOUNDARY_SENTINEL") != std::string::npos);

  uint64_t middle = jd_char_count(handle) / 2;
  assert(jd_read(handle, middle, 4096, &result) == JD_OK);
  assert(!take(&result).empty());

  std::atomic<int> failures{0};
  std::vector<std::thread> workers;
  for (int worker = 0; worker < 8; ++worker) {
    workers.emplace_back([&, worker]() {
      for (int iteration = 0; iteration < 100; ++iteration) {
        uint64_t offset = (middle + static_cast<uint64_t>(worker * 997 + iteration * 113)) %
                          jd_char_count(handle);
        jd_buffer local{};
        if (jd_read(handle, offset, 512, &local) != JD_OK || local.size == 0) {
          ++failures;
        }
        jd_buffer_free(&local);
      }
    });
  }
  for (auto& worker : workers) worker.join();
  assert(failures.load() == 0);

  for (int i = 0; i < 256; ++i) {
    jd_handle extra = 0;
    assert(jd_open_utf8(path, &extra) == JD_OK);
    jd_close(extra);
  }

  jd_close(handle);
  std::remove(path);
  return 0;
}
