#include "index_cache.h"

#include <cstdio>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <sys/stat.h>

namespace jingdu {
namespace {

constexpr const char* kMagic = "JDX1";
std::mutex g_cache_mutex;

struct FileIdentity {
  uint64_t bytes = 0;
  int64_t modified_seconds = 0;
};

bool file_identity(const std::string& path, FileIdentity* identity) {
  if (identity == nullptr) return false;
  struct stat value {};
  if (stat(path.c_str(), &value) != 0 || value.st_size < 0) return false;
  identity->bytes = static_cast<uint64_t>(value.st_size);
  identity->modified_seconds = static_cast<int64_t>(value.st_mtime);
  return true;
}

bool sane_count(uint64_t file_bytes, uint64_t stride, uint64_t count) {
  if (stride == 0 || count == 0) return false;
  const uint64_t theoretical = file_bytes / stride + 2;
  return count <= std::max<uint64_t>(theoretical, 2);
}

}  // namespace

std::string index_cache_path(const std::string& document_path) {
  return document_path + ".jdx";
}

bool load_index_cache(const std::string& document_path, uint64_t stride, uint64_t* bytes,
                      uint64_t* chars, std::vector<IndexPoint>* points) {
  if (bytes == nullptr || chars == nullptr || points == nullptr || stride == 0) return false;

  std::lock_guard<std::mutex> lock(g_cache_mutex);
  FileIdentity current;
  if (!file_identity(document_path, &current)) return false;

  std::ifstream input(index_cache_path(document_path));
  if (!input) return false;

  std::string magic;
  std::getline(input, magic);
  if (magic != kMagic) return false;

  uint64_t cached_bytes = 0;
  int64_t cached_modified = 0;
  uint64_t cached_stride = 0;
  uint64_t cached_chars = 0;
  uint64_t count = 0;
  if (!(input >> cached_bytes >> cached_modified >> cached_stride >> cached_chars >> count)) {
    return false;
  }
  if (cached_bytes != current.bytes || cached_modified != current.modified_seconds ||
      cached_stride != stride || !sane_count(cached_bytes, cached_stride, count)) {
    return false;
  }

  std::vector<IndexPoint> loaded;
  loaded.reserve(static_cast<size_t>(count));
  uint64_t previous_chars = 0;
  uint64_t previous_bytes = 0;
  for (uint64_t index = 0; index < count; ++index) {
    uint64_t char_offset = 0;
    uint64_t byte_offset = 0;
    if (!(input >> char_offset >> byte_offset)) return false;
    if (index == 0) {
      if (char_offset != 0 || byte_offset != 0) return false;
    } else if (char_offset <= previous_chars || byte_offset <= previous_bytes) {
      return false;
    }
    if (char_offset > cached_chars || byte_offset > cached_bytes) return false;
    loaded.emplace_back(char_offset, byte_offset);
    previous_chars = char_offset;
    previous_bytes = byte_offset;
  }

  *bytes = cached_bytes;
  *chars = cached_chars;
  *points = std::move(loaded);
  return true;
}

void save_index_cache(const std::string& document_path, uint64_t stride, uint64_t bytes,
                      uint64_t chars, const std::vector<IndexPoint>& points) {
  if (document_path.empty() || stride == 0 || points.empty()) return;

  FileIdentity current;
  if (!file_identity(document_path, &current) || current.bytes != bytes) return;

  std::lock_guard<std::mutex> lock(g_cache_mutex);
  const std::string target = index_cache_path(document_path);
  const std::string temporary = target + ".tmp";
  {
    std::ofstream output(temporary, std::ios::trunc);
    if (!output) return;
    output << kMagic << '\n';
    output << bytes << ' ' << current.modified_seconds << ' ' << stride << ' ' << chars << ' '
           << points.size() << '\n';
    for (const auto& point : points) {
      output << point.first << ' ' << point.second << '\n';
    }
    output.flush();
    if (!output) {
      output.close();
      std::remove(temporary.c_str());
      return;
    }
  }

  if (std::rename(temporary.c_str(), target.c_str()) != 0) {
    std::remove(temporary.c_str());
  }
}

}  // namespace jingdu
