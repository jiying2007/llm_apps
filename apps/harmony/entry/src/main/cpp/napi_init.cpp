#include <napi/native_api.h>
#include <napi/native_node_api.h>
#include <algorithm>
#include <cstdint>
#include <string>
#include "jingdu/core_api.h"

namespace {
void throw_status(napi_env env, int status, const char* op) {
  if (status == JD_OK) return;
  std::string message = std::string(op) + " failed: " + std::to_string(status);
  napi_throw_error(env, nullptr, message.c_str());
}
bool number(napi_env env, napi_value value, uint64_t* out) {
  double v = 0; if (napi_get_value_double(env, value, &v) != napi_ok || v < 0) return false;
  *out = static_cast<uint64_t>(v); return true;
}
bool string_value(napi_env env, napi_value value, std::string* out) {
  size_t size = 0; if (napi_get_value_string_utf8(env, value, nullptr, 0, &size) != napi_ok) return false;
  out->resize(size); size_t written = 0;
  if (napi_get_value_string_utf8(env, value, out->data(), size + 1, &written) != napi_ok) return false;
  out->resize(written); return true;
}
bool bytes_value(napi_env env, napi_value value, const uint8_t** data, size_t* size) {
  bool typed = false; if (napi_is_typedarray(env, value, &typed) != napi_ok || !typed) return false;
  napi_typedarray_type type; size_t length = 0; void* raw = nullptr; napi_value buffer; size_t offset = 0;
  if (napi_get_typedarray_info(env, value, &type, &length, &raw, &buffer, &offset) != napi_ok || type != napi_uint8_array) return false;
  *data = static_cast<const uint8_t*>(raw); *size = length; return true;
}
napi_value text(napi_env env, const char* data, size_t size) { napi_value out; napi_create_string_utf8(env, data ? data : "", size, &out); return out; }
napi_value boolean(napi_env env, bool value) { napi_value out; napi_get_boolean(env, value, &out); return out; }
napi_value undefined(napi_env env) { napi_value out; napi_get_undefined(env, &out); return out; }
napi_value core_error(napi_env env, int status, const char* op) { throw_status(env, status, op); return nullptr; }

napi_value Abi(napi_env env, napi_callback_info) { napi_value out; napi_create_uint32(env, jd_abi_version(), &out); return out; }
napi_value Detect(napi_env env, napi_callback_info info) {
  size_t argc = 1; napi_value args[1]; napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  const uint8_t* data = nullptr; size_t size = 0; if (argc < 1 || !bytes_value(env, args[0], &data, &size)) { napi_throw_type_error(env,nullptr,"detectEncoding expects Uint8Array"); return nullptr; }
  char name[32]{}; int status = jd_detect_encoding(data, size, name, sizeof(name)); if (status != JD_OK) return core_error(env,status,"detectEncoding"); return text(env,name,std::char_traits<char>::length(name));
}
napi_value Open(napi_env env, napi_callback_info info) {
  size_t argc=1; napi_value args[1]; napi_get_cb_info(env,info,&argc,args,nullptr,nullptr); std::string path;
  if(argc<1||!string_value(env,args[0],&path)){napi_throw_type_error(env,nullptr,"open expects path string");return nullptr;}
  jd_handle h=0; int status=jd_open_utf8(path.c_str(),&h); if(status!=JD_OK)return core_error(env,status,"open"); napi_value out; napi_create_double(env,static_cast<double>(h),&out); return out;
}
napi_value Close(napi_env env,napi_callback_info info){size_t argc=1;napi_value args[1];napi_get_cb_info(env,info,&argc,args,nullptr,nullptr);uint64_t h=0;if(argc<1||!number(env,args[0],&h)){napi_throw_type_error(env,nullptr,"close expects handle");return nullptr;}jd_close(h);return undefined(env);}
napi_value CharCount(napi_env env,napi_callback_info info){size_t argc=1;napi_value args[1];napi_get_cb_info(env,info,&argc,args,nullptr,nullptr);uint64_t h=0;if(argc<1||!number(env,args[0],&h)){napi_throw_type_error(env,nullptr,"charCount expects handle");return nullptr;}napi_value out;napi_create_double(env,static_cast<double>(jd_char_count(h)),&out);return out;}
napi_value Read(napi_env env,napi_callback_info info){size_t argc=3;napi_value a[3];napi_get_cb_info(env,info,&argc,a,nullptr,nullptr);uint64_t h=0,o=0,c=0;if(argc<3||!number(env,a[0],&h)||!number(env,a[1],&o)||!number(env,a[2],&c)){napi_throw_type_error(env,nullptr,"read expects handle, offset, count");return nullptr;}jd_buffer b{};int s=jd_read(h,o,c,&b);if(s!=JD_OK)return core_error(env,s,"read");napi_value out=text(env,b.data,b.size);jd_buffer_free(&b);return out;}
napi_value Search(napi_env env,napi_callback_info info){size_t argc=3;napi_value a[3];napi_get_cb_info(env,info,&argc,a,nullptr,nullptr);uint64_t h=0,limit=0;std::string q;if(argc<3||!number(env,a[0],&h)||!string_value(env,a[1],&q)||!number(env,a[2],&limit)){napi_throw_type_error(env,nullptr,"search expects handle, query, limit");return nullptr;}jd_buffer b{};int s=jd_search(h,q.c_str(),static_cast<uint32_t>(std::min<uint64_t>(limit,10000)),&b);if(s!=JD_OK)return core_error(env,s,"search");napi_value out=text(env,b.data,b.size);jd_buffer_free(&b);return out;}
napi_value Chapters(napi_env env,napi_callback_info info){size_t argc=2;napi_value a[2];napi_get_cb_info(env,info,&argc,a,nullptr,nullptr);uint64_t h=0,limit=0;if(argc<2||!number(env,a[0],&h)||!number(env,a[1],&limit)){napi_throw_type_error(env,nullptr,"chapters expects handle, limit");return nullptr;}jd_buffer b{};int s=jd_chapters(h,static_cast<uint32_t>(std::min<uint64_t>(limit,20000)),&b);if(s!=JD_OK)return core_error(env,s,"chapters");napi_value out=text(env,b.data,b.size);jd_buffer_free(&b);return out;}
napi_value Speech(napi_env env,napi_callback_info info){size_t argc=3;napi_value a[3];napi_get_cb_info(env,info,&argc,a,nullptr,nullptr);uint64_t h=0,o=0,c=0;if(argc<3||!number(env,a[0],&h)||!number(env,a[1],&o)||!number(env,a[2],&c)){napi_throw_type_error(env,nullptr,"speechChunk expects handle, offset, count");return nullptr;}jd_buffer b{};int s=jd_speech_chunk(h,o,c,&b);if(s!=JD_OK)return core_error(env,s,"speechChunk");napi_value out=text(env,b.data,b.size);jd_buffer_free(&b);return out;}
napi_value ExportRules(napi_env env,napi_callback_info info){size_t argc=3;napi_value a[3];napi_get_cb_info(env,info,&argc,a,nullptr,nullptr);uint64_t h=0;std::string rules,path;if(argc<3||!number(env,a[0],&h)||!string_value(env,a[1],&rules)||!string_value(env,a[2],&path)){napi_throw_type_error(env,nullptr,"exportRules expects handle, rules, path");return nullptr;}int s=jd_export_rules(h,rules.c_str(),path.c_str());if(s!=JD_OK)return core_error(env,s,"exportRules");return boolean(env,true);}

napi_value Init(napi_env env,napi_value exports){
  napi_property_descriptor props[]={
    {"abiVersion",nullptr,Abi,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"detectEncoding",nullptr,Detect,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"open",nullptr,Open,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"close",nullptr,Close,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"charCount",nullptr,CharCount,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"read",nullptr,Read,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"search",nullptr,Search,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"chapters",nullptr,Chapters,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"speechChunk",nullptr,Speech,nullptr,nullptr,nullptr,napi_default,nullptr},
    {"exportRules",nullptr,ExportRules,nullptr,nullptr,nullptr,napi_default,nullptr}
  };
  napi_define_properties(env,exports,sizeof(props)/sizeof(props[0]),props); return exports;
}
}

static napi_module module={1,0,nullptr,Init,"entry",nullptr,{0}};
extern "C" __attribute__((constructor)) void RegisterEntryModule(void){napi_module_register(&module);}
