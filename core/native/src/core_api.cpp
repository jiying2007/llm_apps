#include "jingdu/core_api.h"

#include <algorithm>
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
constexpr uint32_t kAbiVersion = 1;
constexpr uint64_t kIndexStride = 4096;
constexpr size_t kScanBuffer = 64 * 1024;
constexpr uint64_t kMaxReadChars = 1024 * 1024;
struct SparsePoint { uint64_t chars; uint64_t bytes; };
struct Document { std::string path; uint64_t bytes = 0; uint64_t chars = 0; std::vector<SparsePoint> index; };
std::mutex g_mutex;
std::unordered_map<jd_handle, std::shared_ptr<Document>> g_docs;
jd_handle g_next_handle = 1;

bool continuation(uint8_t c) { return (c & 0xC0U) == 0x80U; }
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
uint64_t count_utf8_chars(const std::string& s) {
  uint64_t count = 0; size_t i = 0;
  while (i < s.size()) { size_t w = utf8_width(reinterpret_cast<const uint8_t*>(s.data() + i), s.size() - i); if (w == 0) return std::numeric_limits<uint64_t>::max(); i += w; ++count; }
  return count;
}
bool is_valid_utf8(const uint8_t* data, uint64_t size) {
  uint64_t i = 0;
  while (i < size) { size_t w = utf8_width(data + i, static_cast<size_t>(std::min<uint64_t>(size - i, 4))); if (w == 0 || i + w > size) return false; i += w; }
  return true;
}
int set_buffer(const std::string& text, jd_buffer* out) {
  if (!out) return JD_EINVAL; out->data = nullptr; out->size = 0; if (text.empty()) return JD_OK;
  char* p = new (std::nothrow) char[text.size() + 1]; if (!p) return JD_ENOMEM;
  std::memcpy(p, text.data(), text.size()); p[text.size()] = '\0'; out->data = p; out->size = static_cast<uint64_t>(text.size()); return JD_OK;
}
std::shared_ptr<Document> get_doc(jd_handle handle) { std::lock_guard<std::mutex> lock(g_mutex); auto it = g_docs.find(handle); return it == g_docs.end() ? nullptr : it->second; }
int build_index(const std::string& path, Document* doc) {
  std::ifstream in(path, std::ios::binary); if (!in) return errno == ENOENT ? JD_ENOENT : JD_EIO;
  doc->index.clear(); doc->index.push_back({0,0}); uint64_t bytes=0, chars=0; std::vector<uint8_t> carry; std::vector<uint8_t> buf(kScanBuffer+4);
  while (in) {
    if (!carry.empty()) std::copy(carry.begin(), carry.end(), buf.begin());
    in.read(reinterpret_cast<char*>(buf.data()+carry.size()), static_cast<std::streamsize>(kScanBuffer));
    const size_t got=static_cast<size_t>(in.gcount()); size_t total=carry.size()+got, i=0; carry.clear();
    while (i<total) {
      const size_t remain=total-i; size_t w=utf8_width(buf.data()+i,std::min<size_t>(remain,4));
      if (w==0) { if (!in.eof() && remain<4 && buf[i]>=0xC2) { carry.assign(buf.begin()+static_cast<long>(i),buf.begin()+static_cast<long>(total)); break; } return JD_EUTF8; }
      if (chars!=0 && chars%kIndexStride==0) doc->index.push_back({chars,bytes}); i+=w; bytes+=w; ++chars;
    }
  }
  if (!carry.empty()) return JD_EUTF8; doc->bytes=bytes; doc->chars=chars; return JD_OK;
}
SparsePoint point_for_char(const Document& doc,uint64_t target){auto it=std::upper_bound(doc.index.begin(),doc.index.end(),target,[](uint64_t v,const SparsePoint&p){return v<p.chars;});if(it==doc.index.begin())return doc.index.front();--it;return *it;}
SparsePoint point_for_byte(const Document& doc,uint64_t target){auto it=std::upper_bound(doc.index.begin(),doc.index.end(),target,[](uint64_t v,const SparsePoint&p){return v<p.bytes;});if(it==doc.index.begin())return doc.index.front();--it;return *it;}
int byte_offset_for_char(const Document& doc,uint64_t target,uint64_t*out){
  if(!out)return JD_EINVAL;if(target>=doc.chars){*out=doc.bytes;return JD_OK;}SparsePoint p=point_for_char(doc,target);std::ifstream in(doc.path,std::ios::binary);if(!in)return JD_EIO;in.seekg(static_cast<std::streamoff>(p.bytes));uint64_t chars=p.chars,bytes=p.bytes;
  while(chars<target){uint8_t seq[4]{};in.read(reinterpret_cast<char*>(&seq[0]),1);if(!in)return JD_EIO;size_t w=1;const uint8_t c=seq[0];if(c>=0xC2&&c<=0xDF)w=2;else if(c>=0xE0&&c<=0xEF)w=3;else if(c>=0xF0&&c<=0xF4)w=4;if(w>1){in.read(reinterpret_cast<char*>(&seq[1]),static_cast<std::streamsize>(w-1));if(!in)return JD_EIO;}if(utf8_width(seq,w)!=w)return JD_EUTF8;bytes+=w;++chars;}*out=bytes;return JD_OK;
}
uint64_t char_offset_for_byte(const Document& doc,uint64_t target){
  if(target>=doc.bytes)return doc.chars;SparsePoint p=point_for_byte(doc,target);std::ifstream in(doc.path,std::ios::binary);if(!in)return p.chars;in.seekg(static_cast<std::streamoff>(p.bytes));uint64_t chars=p.chars,bytes=p.bytes;
  while(bytes<target){uint8_t seq[4]{};in.read(reinterpret_cast<char*>(&seq[0]),1);if(!in)break;size_t w=1;if(seq[0]>=0xC2&&seq[0]<=0xDF)w=2;else if(seq[0]>=0xE0&&seq[0]<=0xEF)w=3;else if(seq[0]>=0xF0&&seq[0]<=0xF4)w=4;if(bytes+w>target)break;if(w>1)in.read(reinterpret_cast<char*>(&seq[1]),static_cast<std::streamsize>(w-1));if(!in||utf8_width(seq,w)!=w)break;bytes+=w;++chars;}return chars;
}
int read_chars(const Document& doc,uint64_t offset,uint64_t max_chars,std::string*out){
  if(!out)return JD_EINVAL;out->clear();if(offset>=doc.chars||max_chars==0)return JD_OK;max_chars=std::min(max_chars,kMaxReadChars);uint64_t byte_offset=0;int status=byte_offset_for_char(doc,offset,&byte_offset);if(status!=JD_OK)return status;std::ifstream in(doc.path,std::ios::binary);if(!in)return JD_EIO;in.seekg(static_cast<std::streamoff>(byte_offset));out->reserve(static_cast<size_t>(std::min<uint64_t>(max_chars*3,4*1024*1024)));
  for(uint64_t n=0;n<max_chars&&in;++n){uint8_t seq[4]{};in.read(reinterpret_cast<char*>(&seq[0]),1);if(!in)break;size_t w=1;if(seq[0]>=0xC2&&seq[0]<=0xDF)w=2;else if(seq[0]>=0xE0&&seq[0]<=0xEF)w=3;else if(seq[0]>=0xF0&&seq[0]<=0xF4)w=4;if(w>1){in.read(reinterpret_cast<char*>(&seq[1]),static_cast<std::streamsize>(w-1));if(!in)return JD_EUTF8;}if(utf8_width(seq,w)!=w)return JD_EUTF8;out->append(reinterpret_cast<const char*>(seq),w);}return JD_OK;
}
std::string trim_ascii(std::string s){auto ws=[](unsigned char c){return c==' '||c=='\t'||c=='\r'||c=='\n';};while(!s.empty()&&ws(static_cast<unsigned char>(s.front())))s.erase(s.begin());while(!s.empty()&&ws(static_cast<unsigned char>(s.back())))s.pop_back();return s;}
std::string lower_ascii(std::string s){for(char&c:s)c=static_cast<char>(std::tolower(static_cast<unsigned char>(c)));return s;}
bool looks_like_chapter(const std::string& raw){std::string s=trim_ascii(raw);if(s.empty()||s.size()>240)return false;std::string low=lower_ascii(s);if(low.rfind("chapter ",0)==0||low.rfind("chapter\t",0)==0)return true;if(s.rfind("第",0)==0)return s.find("章")!=std::string::npos||s.find("回")!=std::string::npos||s.find("节")!=std::string::npos||s.find("卷")!=std::string::npos;return false;}
std::string context_for(const Document&doc,uint64_t hit){uint64_t start=hit>30?hit-30:0;std::string text;if(read_chars(doc,start,100,&text)!=JD_OK)return{};for(char&c:text)if(c=='\n'||c=='\r'||c=='\t')c=' ';return text;}
std::vector<std::pair<std::string,std::string>> parse_rules(const char*packed){std::vector<std::pair<std::string,std::string>>r;if(!packed)return r;std::string all(packed);size_t start=0;while(start<=all.size()){size_t end=all.find('\x1e',start);if(end==std::string::npos)end=all.size();std::string rec=all.substr(start,end-start);size_t sep=rec.find('\x1f');if(sep!=std::string::npos&&sep>0)r.emplace_back(rec.substr(0,sep),rec.substr(sep+1));if(end==all.size())break;start=end+1;}return r;}
void replace_all(std::string*text,const std::string&from,const std::string&to){if(!text||from.empty())return;size_t pos=0;while((pos=text->find(from,pos))!=std::string::npos){text->replace(pos,from.size(),to);pos+=to.size();}}
bool is_sentence_boundary(const std::string&s,size_t i){unsigned char c=static_cast<unsigned char>(s[i]);if(c=='.'||c=='!'||c=='?'||c=='\n')return true;static const char*marks[]={"。","！","？","；"};for(const char*mark:marks){size_t len=std::strlen(mark);if(i+len<=s.size()&&s.compare(i,len,mark)==0)return true;}return false;}
}

uint32_t jd_abi_version(void){return kAbiVersion;} const char* jd_core_version(void){return "2.0.0";}
int jd_detect_encoding(const uint8_t*data,uint64_t size,char*out_name,uint64_t out_capacity){if((!data&&size!=0)||!out_name||out_capacity==0)return JD_EINVAL;const char*name="GB18030";if(size>=3&&data[0]==0xEF&&data[1]==0xBB&&data[2]==0xBF)name="UTF-8";else if(size>=2&&data[0]==0xFF&&data[1]==0xFE)name="UTF-16LE";else if(size>=2&&data[0]==0xFE&&data[1]==0xFF)name="UTF-16BE";else if(is_valid_utf8(data,size))name="UTF-8";size_t need=std::strlen(name)+1;if(need>out_capacity)return JD_EINVAL;std::memcpy(out_name,name,need);return JD_OK;}
int jd_open_utf8(const char*path,jd_handle*out_handle){if(!path||!*path||!out_handle)return JD_EINVAL;auto doc=std::make_shared<Document>();doc->path=path;int status=build_index(path,doc.get());if(status!=JD_OK)return status;std::lock_guard<std::mutex>lock(g_mutex);jd_handle handle=g_next_handle++;if(handle==0)handle=g_next_handle++;g_docs.emplace(handle,std::move(doc));*out_handle=handle;return JD_OK;}
void jd_close(jd_handle handle){std::lock_guard<std::mutex>lock(g_mutex);g_docs.erase(handle);}uint64_t jd_char_count(jd_handle handle){auto doc=get_doc(handle);return doc?doc->chars:0;}uint64_t jd_byte_count(jd_handle handle){auto doc=get_doc(handle);return doc?doc->bytes:0;}
int jd_read(jd_handle handle,uint64_t char_offset,uint64_t max_chars,jd_buffer*out){auto doc=get_doc(handle);if(!doc)return JD_EHANDLE;std::string text;int status=read_chars(*doc,char_offset,max_chars,&text);return status==JD_OK?set_buffer(text,out):status;}
int jd_search(jd_handle handle,const char*utf8_query,uint32_t limit,jd_buffer*out){auto doc=get_doc(handle);if(!doc)return JD_EHANDLE;if(!utf8_query||!*utf8_query||!out)return JD_EINVAL;std::string query(utf8_query);if(count_utf8_chars(query)==std::numeric_limits<uint64_t>::max())return JD_EUTF8;if(limit==0)limit=100;limit=std::min<uint32_t>(limit,10000);std::ifstream in(doc->path,std::ios::binary);if(!in)return JD_EIO;size_t overlap=query.size()>1?query.size()-1:0;std::string carry;std::vector<char>buf(kScanBuffer);uint64_t base=0,last=std::numeric_limits<uint64_t>::max();uint32_t hits=0;std::ostringstream result;while(in&&hits<limit){in.read(buf.data(),static_cast<std::streamsize>(buf.size()));size_t got=static_cast<size_t>(in.gcount());if(got==0)break;std::string chunk=carry;chunk.append(buf.data(),got);uint64_t chunk_base=base-static_cast<uint64_t>(carry.size());size_t pos=0;while(hits<limit&&(pos=chunk.find(query,pos))!=std::string::npos){uint64_t absolute=chunk_base+pos;if(absolute!=last){uint64_t co=char_offset_for_byte(*doc,absolute);result<<co<<'\t'<<context_for(*doc,co)<<'\n';last=absolute;++hits;}pos+=std::max<size_t>(1,query.size());}carry=overlap==0?std::string():chunk.substr(chunk.size()>overlap?chunk.size()-overlap:0);base+=got;}return set_buffer(result.str(),out);}
int jd_chapters(jd_handle handle,uint32_t limit,jd_buffer*out){auto doc=get_doc(handle);if(!doc)return JD_EHANDLE;if(!out)return JD_EINVAL;if(limit==0)limit=20000;limit=std::min<uint32_t>(limit,20000);std::ifstream in(doc->path,std::ios::binary);if(!in)return JD_EIO;std::string line;uint64_t char_offset=0;uint32_t count=0;std::ostringstream result;while(count<limit&&std::getline(in,line)){if(looks_like_chapter(line)){std::string title=trim_ascii(line);for(char&c:title)if(c=='\t')c=' ';result<<char_offset<<'\t'<<title<<'\n';++count;}uint64_t lc=count_utf8_chars(line);if(lc==std::numeric_limits<uint64_t>::max())return JD_EUTF8;char_offset+=lc;if(!in.eof())++char_offset;}return set_buffer(result.str(),out);}
int jd_speech_chunk(jd_handle handle,uint64_t char_offset,uint64_t max_chars,jd_buffer*out){auto doc=get_doc(handle);if(!doc)return JD_EHANDLE;if(!out||max_chars==0)return JD_EINVAL;max_chars=std::min<uint64_t>(max_chars,4000);std::string text;int status=read_chars(*doc,char_offset,max_chars,&text);if(status!=JD_OK)return status;if(text.empty())return set_buffer("",out);size_t min_byte=text.size()/3,cut=text.size();for(size_t i=min_byte;i<text.size();++i){if(is_sentence_boundary(text,i)){cut=i+1;while(cut<text.size()&&continuation(static_cast<uint8_t>(text[cut])))++cut;break;}}text.resize(cut);uint64_t chunk=count_utf8_chars(text);if(chunk==std::numeric_limits<uint64_t>::max())return JD_EUTF8;std::ostringstream result;result<<(char_offset+chunk)<<'\t'<<text;return set_buffer(result.str(),out);}
int jd_export_rules(jd_handle handle,const char*packed_rules,const char*output_path){auto doc=get_doc(handle);if(!doc)return JD_EHANDLE;if(!output_path||!*output_path)return JD_EINVAL;auto rules=parse_rules(packed_rules);std::ifstream in(doc->path,std::ios::binary);if(!in)return JD_EIO;std::string temp=std::string(output_path)+".tmp";std::ofstream out(temp,std::ios::binary|std::ios::trunc);if(!out)return JD_EIO;std::string line;while(std::getline(in,line)){for(const auto&rule:rules)replace_all(&line,rule.first,rule.second);out.write(line.data(),static_cast<std::streamsize>(line.size()));if(!in.eof())out.put('\n');if(!out){out.close();std::remove(temp.c_str());return JD_EIO;}}out.flush();out.close();if(std::rename(temp.c_str(),output_path)!=0){std::remove(temp.c_str());return JD_EIO;}return JD_OK;}
void jd_buffer_free(jd_buffer*buffer){if(!buffer)return;delete[]buffer->data;buffer->data=nullptr;buffer->size=0;}
