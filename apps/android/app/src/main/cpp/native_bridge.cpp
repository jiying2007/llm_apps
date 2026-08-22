#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <string>
#include "jingdu/core_api.h"

namespace {
std::string bytes(JNIEnv* env, jbyteArray value) {
    if (!value) return {};
    jsize size = env->GetArrayLength(value);
    std::string out(static_cast<size_t>(size), '\0');
    if (size > 0) env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(out.data()));
    return out;
}
jbyteArray array(JNIEnv* env, const char* data, uint64_t size) {
    if (size > static_cast<uint64_t>(INT32_MAX)) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(size));
    if (out && size) env->SetByteArrayRegion(out, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
    return out;
}
jbyteArray take(JNIEnv* env, jd_buffer* buffer) { jbyteArray out = array(env, buffer->data, buffer->size); jd_buffer_free(buffer); return out; }
void fail(JNIEnv* env, int status, const char* operation) {
    if (status == JD_OK) return;
    jclass type = env->FindClass("java/io/IOException");
    std::string message = std::string(operation) + " failed: " + std::to_string(status);
    env->ThrowNew(type, message.c_str());
}
jint abi(JNIEnv*, jclass) { return static_cast<jint>(jd_abi_version()); }
jstring detect(JNIEnv* env, jclass, jbyteArray sample) { std::string input = bytes(env, sample); char name[32]{}; int s = jd_detect_encoding(reinterpret_cast<const uint8_t*>(input.data()), input.size(), name, sizeof(name)); if (s != JD_OK) { fail(env, s, "detectEncoding"); return nullptr; } return env->NewStringUTF(name); }
jlong openDoc(JNIEnv* env, jclass, jbyteArray pathBytes) { std::string path = bytes(env, pathBytes); jd_handle h = 0; int s = jd_open_utf8(path.c_str(), &h); if (s != JD_OK) { fail(env, s, "open"); return 0; } return static_cast<jlong>(h); }
void closeDoc(JNIEnv*, jclass, jlong h) { jd_close(static_cast<jd_handle>(h)); }
jlong charCount(JNIEnv*, jclass, jlong h) { return static_cast<jlong>(jd_char_count(static_cast<jd_handle>(h))); }
jbyteArray readDoc(JNIEnv* env, jclass, jlong h, jlong o, jlong c) { jd_buffer b{}; int s = jd_read(static_cast<jd_handle>(h), static_cast<uint64_t>(std::max<jlong>(0,o)), static_cast<uint64_t>(std::max<jlong>(0,c)), &b); if (s != JD_OK) { fail(env,s,"read"); return nullptr; } return take(env,&b); }
jbyteArray searchDoc(JNIEnv* env, jclass, jlong h, jbyteArray q, jint limit) { std::string query=bytes(env,q); jd_buffer b{}; int s=jd_search(static_cast<jd_handle>(h),query.c_str(),static_cast<uint32_t>(std::max(1,limit)),&b); if(s!=JD_OK){fail(env,s,"search");return nullptr;} return take(env,&b); }
jbyteArray chapters(JNIEnv* env, jclass, jlong h, jint limit) { jd_buffer b{}; int s=jd_chapters(static_cast<jd_handle>(h),static_cast<uint32_t>(std::max(1,limit)),&b); if(s!=JD_OK){fail(env,s,"chapters");return nullptr;} return take(env,&b); }
jbyteArray speech(JNIEnv* env, jclass, jlong h, jlong o, jlong c) { jd_buffer b{}; int s=jd_speech_chunk(static_cast<jd_handle>(h),static_cast<uint64_t>(std::max<jlong>(0,o)),static_cast<uint64_t>(std::max<jlong>(1,c)),&b); if(s!=JD_OK){fail(env,s,"speechChunk");return nullptr;} return take(env,&b); }
void exportRules(JNIEnv* env, jclass, jlong h, jbyteArray r, jbyteArray p) { std::string rules=bytes(env,r),path=bytes(env,p); int s=jd_export_rules(static_cast<jd_handle>(h),rules.c_str(),path.c_str()); if(s!=JD_OK)fail(env,s,"exportRules"); }
JNINativeMethod methods[]={{"nativeAbiVersion","()I",reinterpret_cast<void*>(abi)},{"nativeDetectEncoding","([B)Ljava/lang/String;",reinterpret_cast<void*>(detect)},{"nativeOpen","([B)J",reinterpret_cast<void*>(openDoc)},{"nativeClose","(J)V",reinterpret_cast<void*>(closeDoc)},{"nativeCharCount","(J)J",reinterpret_cast<void*>(charCount)},{"nativeRead","(JJJ)[B",reinterpret_cast<void*>(readDoc)},{"nativeSearch","(J[BI)[B",reinterpret_cast<void*>(searchDoc)},{"nativeChapters","(JI)[B",reinterpret_cast<void*>(chapters)},{"nativeSpeechChunk","(JJJ)[B",reinterpret_cast<void*>(speech)},{"nativeExportRules","(J[B[B)V",reinterpret_cast<void*>(exportRules)}};
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) { JNIEnv* env=nullptr; if(vm->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6)!=JNI_OK)return JNI_ERR; jclass cls=env->FindClass("com/junchen/jingdu/NativeCore"); if(!cls)return JNI_ERR; if(env->RegisterNatives(cls,methods,sizeof(methods)/sizeof(methods[0]))!=JNI_OK)return JNI_ERR; return JNI_VERSION_1_6; }
