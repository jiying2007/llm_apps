#include "jingdu/core_api.h"
#include "sha256.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {
constexpr uint32_t kAbiVersion = 2;
constexpr uint64_t kIndexStride = 4096;
constexpr size_t kScanBuffer = 64 * 1024;
constexpr uint64_t kMaxReadChars = 1024 * 1024;

struct SparsePoint { uint64_t chars; uint64_t bytes; };
struct Document { std::string path; uint64_t bytes = 0; uint64_t chars = 0; std::vector<SparsePoint> index; };
struct LegacyScore { bool valid = false; uint32_t pairs = 0; uint32_t common_hits = 0; };

std::mutex g_mutex;
std::unordered_map<jd_handle, std::shared_ptr<Document>> g_docs;
jd_handle g_next_handle = 1;

constexpr std::array<uint16_t, 122> kCommonGb18030 = {0x81ED,0x8283,0x87F8,0x8C57,0x8CA6,0x9572,0x95F8,0x95FE,0x9EB3,0x9EE9,0x9F6F,0xAC46,0xB06C,0xB1BE,0xB2BB,0xB3A4,0xB3C9,0xB3F6,0xB4CB,0xB4CE,0xB4F3,0xB5BD,0xB5C0,0xB5C3,0xB5C4,0xB5D8,0xB5DA,0xB6C1,0xB6D4,0xB6E0,0xB6F8,0xB6FE,0xB7A2,0xB8DF,0xB8F6,0xB9FA,0xB9FD,0xBAC3,0xBAF3,0xBBB9,0xBBE1,0xBCD2,0xBDF8,0xBE57,0xBECD,0xBFAA,0xBFB4,0xC0B4,0xC0EF,0xC1CB,0xC295,0xC3C7,0xC3E6,0xC3F7,0xC4DC,0xC4E3,0xC4EA,0xC55F,0xC563,0xC6F0,0xC7B0,0xC8A5,0xC8BB,0xC8CB,0xC8D5,0xC8FD,0xC9CF,0xC9F9,0xC9FA,0xCAB1,0xCAC2,0xCAC7,0xCAD6,0xCAE9,0xCBB5,0xCBFB,0xCBFD,0xCCA8,0xCCE5,0xCCEC,0xCDE5,0xCDF8,0xCEAA,0xCEC4,0xCED2,0xCEDE,0xCFC2,0xCFD6,0xCFEB,0xD0A1,0xD0C4,0xD0D0,0xD165,0xD1A7,0xD2B2,0xD2BB,0xD2D1,0xD3C3,0xD3D0,0xD3DA,0xD3EB,0xD4C2,0xD4DA,0xD566,0xD5C2,0xD5E2,0xD6BB,0xD6D0,0xD6F8,0xD778,0xD7C5,0xD7D3,0xD7EE,0xDF40,0xDF4D,0xDF5E,0xDF80,0xE1E1,0xE94C,0xE95F,0xECB6,0xF377};
constexpr std::array<uint16_t, 98> kCommonBig5 = {0xA440,0xA446,0xA447,0xA448,0xA454,0xA455,0xA457,0xA45D,0xA45F,0xA46A,0xA46C,0xA470,0xA477,0xA4A3,0xA4A4,0xA4D1,0xA4DF,0xA4E2,0xA4E5,0xA4E9,0xA4EB,0xA54C,0xA558,0xA568,0xA575,0xA578,0xA5BB,0xA5CD,0xA5CE,0xA65A,0xA661,0xA662,0xA668,0xA66E,0xA66F,0xA67E,0xA6A8,0xA6B3,0xA6B8,0xA6B9,0xA6D3,0xA6E6,0xA741,0xA7DA,0xA8BD,0xA8C6,0xA8D3,0xA8EC,0xA9F3,0xA9FA,0xAABA,0xAAF8,0xAB65,0xABE1,0xAC4F,0xACB0,0xACDD,0xADB1,0xADCC,0xAE61,0xAEC9,0xAED1,0xAFE0,0xB05F,0xB0AA,0xB0EA,0xB16F,0xB27B,0xB2C4,0xB36F,0xB3B9,0xB3CC,0xB44E,0xB54C,0xB54D,0xB56F,0xB5DB,0xB669,0xB67D,0xB751,0xB77C,0xB8CC,0xB944,0xB94C,0xB9EF,0xBAF4,0xBB4F,0xBB50,0xBBA1,0xBEC7,0xC16E,0xC1D9,0xC5AA,0xC5E9,0xC657,0xC94F,0xCA49,0xCA5E};

bool continuation(uint8_t v) { return (v & 0xC0U) == 0x80U; }

size_t utf8_width(const uint8_t* p, size_t remain) {
  if (remain == 0) return 0;
  const uint8_t c = p[0];
  if (c <= 0x7F) return 1;
  if (c >= 0xC2 && c <= 0xDF) return remain >= 2 && continuation(p[1]) ? 2 : 0;
  if (c == 0xE0) return remain >= 3 && p[1] >= 0xA0 && p[1] <= 0xBF && continuation(p[2]) ? 3 : 0;
  if ((c >= 0xE1 && c <= 0xEC) || (c >= 0xEE && c <= 0xEF)) return remain >= 3 && continuation(p[1]) && continuation(p[2]) ? 3 : 0;
  if (c == 0xED) return remain >= 3 && p[1] >= 0x80 && p[1] <= 0x9F && continuation(p[2]) ? 3 : 0;
  if (c == 0xF0) return remain >= 4 && p[1] >= 0x90 && p[1] <= 0xBF && continuation(p[2]) && continuation(p[3]) ? 4 : 0;
  if (c >= 0xF1 && c <= 0xF3) return remain >= 4 && continuation(p[1]) && continuation(p[2]) && continuation(p[3]) ? 4 : 0;
  if (c == 0xF4) return remain >= 4 && p[1] >= 0x80 && p[1] <= 0x8F && continuation(p[2]) && continuation(p[3]) ? 4 : 0;
  return 0;
}

size_t expected_utf8_width(uint8_t c) {
  if (c <= 0x7F) return 1;
  if (c >= 0xC2 && c <= 0xDF) return 2;
  if (c >= 0xE0 && c <= 0xEF) return 3;
  if (c >= 0xF0 && c <= 0xF4) return 4;
  return 0;
}

bool valid_utf8_sample(const uint8_t* data, uint64_t size, bool truncated) {
  uint64_t offset = 0;
  while (offset < size) {
    const size_t remaining = static_cast<size_t>(std::min<uint64_t>(size - offset, 4));
    const size_t width = utf8_width(data + offset, remaining);
    if (width != 0) { offset += width; continue; }
    if (truncated) {
      const size_t expected = expected_utf8_width(data[offset]);
      const uint64_t tail = size - offset;
      if (expected > tail && expected <= 4) {
        for (uint64_t i = 1; i < tail; ++i) if (!continuation(data[offset + i])) return false;
        return true;
      }
    }
    return false;
  }
  return true;
}

uint64_t count_utf8_chars(const std::string& text) {
  uint64_t count = 0;
  size_t offset = 0;
  while (offset < text.size()) {
    const size_t width = utf8_width(reinterpret_cast<const uint8_t*>(text.data() + offset), text.size() - offset);
    if (width == 0) return std::numeric_limits<uint64_t>::max();
    offset += width; ++count;
  }
  return count;
}

const char* detect_utf16_zero_pattern(const uint8_t* data, uint64_t size) {
  if (size < 8) return nullptr;
  const uint64_t pairs = size / 2;
  uint64_t even = 0, odd = 0;
  for (uint64_t i = 0; i + 1 < size; i += 2) { if (data[i] == 0) ++even; if (data[i + 1] == 0) ++odd; }
  const double er = static_cast<double>(even) / static_cast<double>(pairs);
  const double oratio = static_cast<double>(odd) / static_cast<double>(pairs);
  if (oratio > 0.20 && er < 0.05) return "UTF-16LE";
  if (er > 0.20 && oratio < 0.05) return "UTF-16BE";
  return nullptr;
}

template <size_t N> bool common_pair(uint16_t pair, const std::array<uint16_t, N>& table) {
  return std::binary_search(table.begin(), table.end(), pair);
}

LegacyScore score_gb18030(const uint8_t* data, uint64_t size, bool truncated) {
  LegacyScore s; s.valid = true; uint64_t i = 0;
  while (i < size) {
    const uint8_t a = data[i];
    if (a <= 0x7F) { ++i; continue; }
    if (a < 0x81 || a > 0xFE) { s.valid = false; return s; }
    if (i + 1 >= size) { s.valid = truncated; return s; }
    const uint8_t b = data[i + 1];
    if (b >= 0x30 && b <= 0x39) {
      if (i + 3 >= size) { s.valid = truncated; return s; }
      const uint8_t c = data[i + 2], d = data[i + 3];
      if (c < 0x81 || c > 0xFE || d < 0x30 || d > 0x39) { s.valid = false; return s; }
      i += 4; ++s.pairs; continue;
    }
    if (!((b >= 0x40 && b <= 0x7E) || (b >= 0x80 && b <= 0xFE)) || b == 0x7F) { s.valid = false; return s; }
    const uint16_t pair = static_cast<uint16_t>((a << 8U) | b);
    if (common_pair(pair, kCommonGb18030)) ++s.common_hits;
    i += 2; ++s.pairs;
  }
  return s;
}

LegacyScore score_big5(const uint8_t* data, uint64_t size, bool truncated) {
  LegacyScore s; s.valid = true; uint64_t i = 0;
  while (i < size) {
    const uint8_t a = data[i];
    if (a <= 0x7F) { ++i; continue; }
    if (a < 0x81 || a > 0xFE) { s.valid = false; return s; }
    if (i + 1 >= size) { s.valid = truncated; return s; }
    const uint8_t b = data[i + 1];
    if (!((b >= 0x40 && b <= 0x7E) || (b >= 0xA1 && b <= 0xFE))) { s.valid = false; return s; }
    const uint16_t pair = static_cast<uint16_t>((a << 8U) | b);
    if (common_pair(pair, kCommonBig5)) ++s.common_hits;
    i += 2; ++s.pairs;
  }
  return s;
}

const char* detect_legacy(const uint8_t* data, uint64_t size, bool truncated) {
  const LegacyScore gb = score_gb18030(data, size, truncated);
  const LegacyScore big5 = score_big5(data, size, truncated);
  if (big5.valid && !gb.valid) return "Big5";
  if (gb.valid && big5.valid && big5.common_hits >= 2) {
    const uint32_t margin = std::max<uint32_t>(2, std::min(gb.pairs, big5.pairs) / 12);
    if (big5.common_hits >= gb.common_hits + margin) return "Big5";
  }
  return "GB18030";
}

const char* choose_encoding(const uint8_t* data, uint64_t size, bool truncated) {
  if (size >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF) return "UTF-8";
  if (size >= 2 && data[0] == 0xFF && data[1] == 0xFE) return "UTF-16LE";
  if (size >= 2 && data[0] == 0xFE && data[1] == 0xFF) return "UTF-16BE";
  if (const char* utf16 = detect_utf16_zero_pattern(data, size)) return utf16;
  if (valid_utf8_sample(data, size, truncated)) return "UTF-8";
  return detect_legacy(data, size, truncated);
}

int copy_hex(const std::string& value, char* out, uint64_t capacity) {
  if (out == nullptr || capacity < JD_SHA256_HEX_SIZE || value.size() != 64) return JD_EINVAL;
  std::memcpy(out, value.c_str(), JD_SHA256_HEX_SIZE);
  return JD_OK;
}

int set_buffer(const std::string& text, jd_buffer* out) {
  if (out == nullptr) return JD_EINVAL;
  out->data = nullptr; out->size = 0;
  if (text.empty()) return JD_OK;
  char* p = new (std::nothrow) char[text.size() + 1];
  if (p == nullptr) return JD_ENOMEM;
  std::memcpy(p, text.data(), text.size()); p[text.size()] = '\0'; out->data = p; out->size = text.size(); return JD_OK;
}

std::shared_ptr<Document> get_doc(jd_handle handle) {
  std::lock_guard<std::mutex> lock(g_mutex);
  const auto it = g_docs.find(handle); return it == g_docs.end() ? nullptr : it->second;
}

int build_index(const std::string& path, Document* doc) {
  if (doc == nullptr) return JD_EINVAL;
  std::ifstream input(path, std::ios::binary); if (!input) return errno == ENOENT ? JD_ENOENT : JD_EIO;
  doc->index = {{0,0}}; uint64_t bytes = 0, chars = 0; std::vector<uint8_t> carry; std::vector<uint8_t> buffer(kScanBuffer + 4);
  while (input) {
    if (!carry.empty()) std::copy(carry.begin(), carry.end(), buffer.begin());
    input.read(reinterpret_cast<char*>(buffer.data() + carry.size()), static_cast<std::streamsize>(kScanBuffer));
    const size_t read = static_cast<size_t>(input.gcount()); const size_t total = carry.size() + read; size_t offset = 0; carry.clear();
    while (offset < total) {
      const size_t remain = total - offset; const size_t width = utf8_width(buffer.data() + offset, std::min<size_t>(remain, 4));
      if (width == 0) {
        if (!input.eof() && remain < 4 && expected_utf8_width(buffer[offset]) > remain) { carry.assign(buffer.begin() + static_cast<std::ptrdiff_t>(offset), buffer.begin() + static_cast<std::ptrdiff_t>(total)); break; }
        return JD_EUTF8;
      }
      if (chars != 0 && chars % kIndexStride == 0) doc->index.push_back({chars, bytes});
      offset += width; bytes += width; ++chars;
    }
  }
  if (!carry.empty()) return JD_EUTF8;
  doc->bytes = bytes; doc->chars = chars; return JD_OK;
}

SparsePoint point_for_char(const Document& doc, uint64_t target) {
  auto it = std::upper_bound(doc.index.begin(), doc.index.end(), target, [](uint64_t v, const SparsePoint& p){ return v < p.chars; });
  if (it == doc.index.begin()) return doc.index.front(); --it; return *it;
}
SparsePoint point_for_byte(const Document& doc, uint64_t target) {
  auto it = std::upper_bound(doc.index.begin(), doc.index.end(), target, [](uint64_t v, const SparsePoint& p){ return v < p.bytes; });
  if (it == doc.index.begin()) return doc.index.front(); --it; return *it;
}

int read_codepoint(std::ifstream* input, uint64_t* width_out, std::string* append_to = nullptr) {
  if (input == nullptr || width_out == nullptr) return JD_EINVAL;
  uint8_t seq[4]{}; input->read(reinterpret_cast<char*>(seq), 1); if (!*input) return JD_EIO;
  const size_t width = expected_utf8_width(seq[0]); if (width == 0) return JD_EUTF8;
  if (width > 1) { input->read(reinterpret_cast<char*>(seq + 1), static_cast<std::streamsize>(width - 1)); if (!*input) return JD_EUTF8; }
  if (utf8_width(seq, width) != width) return JD_EUTF8;
  if (append_to) append_to->append(reinterpret_cast<const char*>(seq), width);
  *width_out = width; return JD_OK;
}

int byte_offset_for_char(const Document& doc, uint64_t target, uint64_t* out) {
  if (out == nullptr) return JD_EINVAL; if (target >= doc.chars) { *out = doc.bytes; return JD_OK; }
  const SparsePoint p = point_for_char(doc, target); std::ifstream input(doc.path, std::ios::binary); if (!input) return JD_EIO;
  input.seekg(static_cast<std::streamoff>(p.bytes)); uint64_t chars = p.chars, bytes = p.bytes;
  while (chars < target) { uint64_t width = 0; const int s = read_codepoint(&input, &width); if (s != JD_OK) return s; bytes += width; ++chars; }
  *out = bytes; return JD_OK;
}

uint64_t char_offset_for_byte(const Document& doc, uint64_t target) {
  if (target >= doc.bytes) return doc.chars;
  const SparsePoint p = point_for_byte(doc, target); std::ifstream input(doc.path, std::ios::binary); if (!input) return p.chars;
  input.seekg(static_cast<std::streamoff>(p.bytes)); uint64_t chars = p.chars, bytes = p.bytes;
  while (bytes < target) { const std::streampos before = input.tellg(); uint64_t width = 0; if (read_codepoint(&input, &width) != JD_OK) break; if (bytes + width > target) { input.clear(); input.seekg(before); break; } bytes += width; ++chars; }
  return chars;
}

int read_chars(const Document& doc, uint64_t offset, uint64_t max_chars, std::string* out) {
  if (out == nullptr) return JD_EINVAL; out->clear(); if (offset >= doc.chars || max_chars == 0) return JD_OK;
  max_chars = std::min(max_chars, kMaxReadChars); uint64_t byte_offset = 0; const int s = byte_offset_for_char(doc, offset, &byte_offset); if (s != JD_OK) return s;
  std::ifstream input(doc.path, std::ios::binary); if (!input) return JD_EIO; input.seekg(static_cast<std::streamoff>(byte_offset));
  out->reserve(static_cast<size_t>(std::min<uint64_t>(max_chars * 3, 4ULL * 1024ULL * 1024ULL)));
  for (uint64_t count = 0; count < max_chars && input; ++count) { uint64_t width = 0; const int status = read_codepoint(&input, &width, out); if (status == JD_EIO && input.eof()) break; if (status != JD_OK) return status; }
  return JD_OK;
}

std::string trim_ascii(std::string v) {
  auto ws=[](unsigned char c){ return c==' '||c=='\t'||c=='\r'||c=='\n'; };
  while(!v.empty()&&ws(static_cast<unsigned char>(v.front()))) v.erase(v.begin());
  while(!v.empty()&&ws(static_cast<unsigned char>(v.back()))) v.pop_back(); return v;
}
std::string lower_ascii(std::string v) { for(char& c:v)c=static_cast<char>(std::tolower(static_cast<unsigned char>(c))); return v; }
bool looks_like_chapter(const std::string& raw) {
  const std::string v=trim_ascii(raw); if(v.empty()||v.size()>240)return false; const std::string l=lower_ascii(v);
  if(l.rfind("chapter ",0)==0||l.rfind("chapter\t",0)==0)return true;
  return v.rfind("第",0)==0&&(v.find("章")!=std::string::npos||v.find("回")!=std::string::npos||v.find("节")!=std::string::npos||v.find("卷")!=std::string::npos);
}
std::string context_for(const Document& doc,uint64_t hit){std::string v; if(read_chars(doc,hit>30?hit-30:0,100,&v)!=JD_OK)return{}; for(char&c:v)if(c=='\n'||c=='\r'||c=='\t')c=' '; return v;}
std::vector<std::pair<std::string,std::string>> parse_rules(const char* packed){std::vector<std::pair<std::string,std::string>> rules;if(!packed)return rules;const std::string all(packed);size_t start=0;while(start<=all.size()){size_t end=all.find('\x1e',start);if(end==std::string::npos)end=all.size();const std::string rec=all.substr(start,end-start);const size_t sep=rec.find('\x1f');if(sep!=std::string::npos&&sep>0)rules.emplace_back(rec.substr(0,sep),rec.substr(sep+1));if(end==all.size())break;start=end+1;}return rules;}
void replace_all(std::string* text,const std::string&from,const std::string&to){if(!text||from.empty())return;size_t p=0;while((p=text->find(from,p))!=std::string::npos){text->replace(p,from.size(),to);p+=to.size();}}
size_t sentence_boundary_width(const std::string& text,size_t offset){const unsigned char c=static_cast<unsigned char>(text[offset]);if(c=='.'||c=='!'||c=='?'||c=='\n')return 1;static constexpr const char* marks[]={"。","！","？","；"};for(const char*m:marks){const size_t n=std::strlen(m);if(offset+n<=text.size()&&text.compare(offset,n,m)==0)return n;}return 0;}
}  // namespace

uint32_t jd_abi_version(void){return kAbiVersion;}
const char* jd_core_version(void){return "2.0.0";}

int jd_detect_encoding(const uint8_t* data,uint64_t size,uint32_t flags,char* out_name,uint64_t out_capacity){
  if((data==nullptr&&size!=0)||out_name==nullptr||out_capacity==0)return JD_EINVAL;
  const char* name=choose_encoding(data,size,(flags&JD_DETECT_SAMPLE_TRUNCATED)!=0);const size_t need=std::strlen(name)+1;if(need>out_capacity)return JD_EINVAL;std::memcpy(out_name,name,need);return JD_OK;
}
int jd_sha256(const uint8_t* data,uint64_t size,char* out_hex,uint64_t out_capacity){if(data==nullptr&&size!=0)return JD_EINVAL;return copy_hex(jingdu::sha256_hex(data,static_cast<size_t>(size)),out_hex,out_capacity);}
int jd_file_sha256(const char* path,char* out_hex,uint64_t out_capacity){if(path==nullptr||*path=='\0')return JD_EINVAL;bool ok=false;const std::string value=jingdu::sha256_file_hex(path,&ok);return ok?copy_hex(value,out_hex,out_capacity):(errno==ENOENT?JD_ENOENT:JD_EIO);}
int jd_repair_revision(const char* normalized_sha256,const char* packed_rules,char* out_hex,uint64_t out_capacity){if(normalized_sha256==nullptr||std::strlen(normalized_sha256)!=64)return JD_EINVAL;const std::string material=std::string("jingdu-repair-v1\n")+normalized_sha256+"\n"+(packed_rules?packed_rules:"");return copy_hex(jingdu::sha256_hex(reinterpret_cast<const uint8_t*>(material.data()),material.size()),out_hex,out_capacity);}

int jd_open_utf8(const char* path,jd_handle* out_handle){if(path==nullptr||*path=='\0'||out_handle==nullptr)return JD_EINVAL;auto doc=std::make_shared<Document>();doc->path=path;const int status=build_index(path,doc.get());if(status!=JD_OK)return status;std::lock_guard<std::mutex>lock(g_mutex);jd_handle h=g_next_handle++;if(h==0)h=g_next_handle++;g_docs.emplace(h,std::move(doc));*out_handle=h;return JD_OK;}
void jd_close(jd_handle handle){std::lock_guard<std::mutex>lock(g_mutex);g_docs.erase(handle);}
uint64_t jd_char_count(jd_handle handle){const auto d=get_doc(handle);return d?d->chars:0;}
uint64_t jd_byte_count(jd_handle handle){const auto d=get_doc(handle);return d?d->bytes:0;}
int jd_read(jd_handle handle,uint64_t char_offset,uint64_t max_chars,jd_buffer* out){const auto d=get_doc(handle);if(!d)return JD_EHANDLE;std::string text;const int s=read_chars(*d,char_offset,max_chars,&text);return s==JD_OK?set_buffer(text,out):s;}
int jd_search(jd_handle handle,const char* query,uint32_t limit,jd_buffer* out){const auto d=get_doc(handle);if(!d)return JD_EHANDLE;if(query==nullptr||*query=='\0'||out==nullptr)return JD_EINVAL;const std::string q(query);if(count_utf8_chars(q)==std::numeric_limits<uint64_t>::max())return JD_EUTF8;if(limit==0)limit=100;limit=std::min<uint32_t>(limit,10000);std::ifstream input(d->path,std::ios::binary);if(!input)return JD_EIO;const size_t overlap=q.size()>1?q.size()-1:0;std::string carry;std::vector<char>buffer(kScanBuffer);uint64_t base=0,last=std::numeric_limits<uint64_t>::max();uint32_t hits=0;std::ostringstream result;while(input&&hits<limit){input.read(buffer.data(),static_cast<std::streamsize>(buffer.size()));const size_t read=static_cast<size_t>(input.gcount());if(read==0)break;std::string chunk=carry;chunk.append(buffer.data(),read);const uint64_t chunk_base=base-static_cast<uint64_t>(carry.size());size_t pos=0;while(hits<limit&&(pos=chunk.find(q,pos))!=std::string::npos){const uint64_t absolute=chunk_base+pos;if(absolute!=last){const uint64_t co=char_offset_for_byte(*d,absolute);result<<co<<'\t'<<context_for(*d,co)<<'\n';last=absolute;++hits;}pos+=std::max<size_t>(1,q.size());}carry=overlap==0?std::string():chunk.substr(chunk.size()>overlap?chunk.size()-overlap:0);base+=read;}return set_buffer(result.str(),out);}
int jd_chapters(jd_handle handle,uint32_t limit,jd_buffer* out){const auto d=get_doc(handle);if(!d)return JD_EHANDLE;if(!out)return JD_EINVAL;if(limit==0)limit=20000;limit=std::min<uint32_t>(limit,20000);std::ifstream input(d->path,std::ios::binary);if(!input)return JD_EIO;std::string line;uint64_t char_offset=0;uint32_t count=0;std::ostringstream result;while(count<limit&&std::getline(input,line)){if(looks_like_chapter(line)){std::string title=trim_ascii(line);for(char&c:title)if(c=='\t')c=' ';result<<char_offset<<'\t'<<title<<'\n';++count;}const uint64_t lc=count_utf8_chars(line);if(lc==std::numeric_limits<uint64_t>::max())return JD_EUTF8;char_offset+=lc;if(!input.eof())++char_offset;}return set_buffer(result.str(),out);}
int jd_speech_chunk(jd_handle handle,uint64_t char_offset,uint64_t max_chars,jd_buffer* out){const auto d=get_doc(handle);if(!d)return JD_EHANDLE;if(!out||max_chars==0)return JD_EINVAL;max_chars=std::min<uint64_t>(max_chars,4000);std::string text;const int s=read_chars(*d,char_offset,max_chars,&text);if(s!=JD_OK)return s;if(text.empty())return set_buffer("",out);const size_t minimum=text.size()/3;size_t cut=text.size();for(size_t i=minimum;i<text.size();++i){const size_t boundary=sentence_boundary_width(text,i);if(boundary){cut=i+boundary;break;}}text.resize(cut);const uint64_t chars=count_utf8_chars(text);if(chars==std::numeric_limits<uint64_t>::max())return JD_EUTF8;std::ostringstream result;result<<(char_offset+chars)<<'\t'<<text;return set_buffer(result.str(),out);}
int jd_export_rules(jd_handle handle,const char* packed_rules,const char* output_path){const auto d=get_doc(handle);if(!d)return JD_EHANDLE;if(output_path==nullptr||*output_path=='\0')return JD_EINVAL;const auto rules=parse_rules(packed_rules);std::ifstream input(d->path,std::ios::binary);if(!input)return JD_EIO;const std::string temp=std::string(output_path)+".tmp";std::ofstream output(temp,std::ios::binary|std::ios::trunc);if(!output)return JD_EIO;std::string line;while(std::getline(input,line)){for(const auto&r:rules)replace_all(&line,r.first,r.second);output.write(line.data(),static_cast<std::streamsize>(line.size()));if(!input.eof())output.put('\n');if(!output){output.close();std::remove(temp.c_str());return JD_EIO;}}output.flush();if(!output){output.close();std::remove(temp.c_str());return JD_EIO;}output.close();if(std::rename(temp.c_str(),output_path)!=0){std::remove(temp.c_str());return JD_EIO;}return JD_OK;}
void jd_buffer_free(jd_buffer* buffer){if(!buffer)return;delete[]buffer->data;buffer->data=nullptr;buffer->size=0;}
