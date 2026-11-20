cmake_minimum_required(VERSION 3.22.1)
project("lexiq_onnx_security" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)

set(LEXIQ_JNI_SRC "${CMAKE_CURRENT_SOURCE_DIR}/lexiq_onnx_security.cpp")
if(NOT EXISTS "${LEXIQ_JNI_SRC}")
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/lexiq_onnx_security_stub.cpp" [=[
#include <jni.h>
#include <android/log.h>

extern "C" JNIEXPORT jstring JNICALL
Java_org_linguistic_assistant_OnnxEngine_nativeEngineStatus(JNIEnv* env, jobject /* thiz */) {
    __android_log_print(ANDROID_LOG_INFO, "LexiQNative", "lexiq_onnx_security stub active");
    return env->NewStringUTF("READY_FALLBACK");
}
]=])
    set(LEXIQ_JNI_SRC "${CMAKE_CURRENT_BINARY_DIR}/lexiq_onnx_security_stub.cpp")
endif()

add_library(lexiq_onnx_security SHARED "${LEXIQ_JNI_SRC}")

target_compile_options(lexiq_onnx_security PRIVATE -fexceptions -frtti)

target_link_libraries(
    lexiq_onnx_security
    ${log-lib}
)
