#include "jingdu/core_api.h"
#include "index_cache.h"

#define jd_open_utf8 jd_open_utf8_uncached_internal
// Intentional translation-unit composition: this wrapper replaces only the public open entry point
// while keeping the ABI v2 implementation private to one compiled translation unit.
// NOLINTNEXTLINE(bugprone-suspicious-include)
#include "core_api.cpp"
#undef jd_open_utf8

extern "C" jd_status jd_open_utf8(const char* path, jd_handle* out_handle) {
  if (path == nullptr || *path == '\0' || out_handle == nullptr) return JD_EINVAL;

  uint64_t bytes = 0;
  uint64_t chars = 0;
  std::vector<jingdu::IndexPoint> points;
  if (jingdu::load_index_cache(path, kIndexStride, &bytes, &chars, &points)) {
    auto document = std::make_shared<Document>();
    document->path = path;
    document->bytes = bytes;
    document->chars = chars;
    document->index.reserve(points.size());
    for (const auto& point : points) {
      document->index.push_back({point.first, point.second});
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    jd_handle handle = g_next_handle++;
    if (handle == 0) handle = g_next_handle++;
    g_documents.emplace(handle, std::move(document));
    *out_handle = handle;
    return JD_OK;
  }

  const jd_status status = jd_open_utf8_uncached_internal(path, out_handle);
  if (status != JD_OK) return status;

  const auto document = getDocument(*out_handle);
  if (document != nullptr) {
    std::vector<jingdu::IndexPoint> generated;
    generated.reserve(document->index.size());
    for (const SparsePoint& point : document->index) {
      generated.emplace_back(point.chars, point.bytes);
    }
    jingdu::save_index_cache(path, kIndexStride, document->bytes, document->chars, generated);
  }
  return JD_OK;
}
