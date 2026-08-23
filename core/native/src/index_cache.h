#ifndef JINGDU_INDEX_CACHE_H
#define JINGDU_INDEX_CACHE_H

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

namespace jingdu {

using IndexPoint = std::pair<uint64_t, uint64_t>;

bool load_index_cache(const std::string& document_path, uint64_t stride, uint64_t* bytes,
                      uint64_t* chars, std::vector<IndexPoint>* points);

void save_index_cache(const std::string& document_path, uint64_t stride, uint64_t bytes,
                      uint64_t chars, const std::vector<IndexPoint>& points);

std::string index_cache_path(const std::string& document_path);

}  // namespace jingdu

#endif
