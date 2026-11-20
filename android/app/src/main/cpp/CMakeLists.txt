cmake_minimum_required(VERSION 3.22.1)
project("lexiq_onnx_security" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)

set(LEXIQ_SECURITY_SRC "${CMAKE_CURRENT_SOURCE_DIR}/lexiq_onnx_security.cpp")
set(LEXIQ_BRIDGE_SRC "${CMAKE_CURRENT_SOURCE_DIR}/native_bridge.cpp")

if(EXISTS "${LEXIQ_SECURITY_SRC}")
    set(NATIVE_SOURCES "${LEXIQ_SECURITY_SRC}")
elseif(EXISTS "${LEXIQ_BRIDGE_SRC}")
    set(NATIVE_SOURCES "${LEXIQ_BRIDGE_SRC}")
else()
    set(GENERATED_SRC "${CMAKE_CURRENT_BINARY_DIR}/lexiq_onnx_security_fallback.cpp")
    file(WRITE "${GENERATED_SRC}" [=[
#include <jni.h>
#include <android/log.h>

extern "C" JNIEXPORT jstring JNICALL
Java_org_linguistic_assistant_OnnxEngine_nativeEngineStatus(JNIEnv* env, jobject /* thiz */) {
    __android_log_print(ANDROID_LOG_INFO, "LexiQNative", "lexiq_onnx_security fallback active");
    return env->NewStringUTF("READY_FALLBACK");
}
]=])
    set(NATIVE_SOURCES "${GENERATED_SRC}")
endif()

add_library(lexiq_onnx_security SHARED ${NATIVE_SOURCES})

target_compile_options(lexiq_onnx_security PRIVATE -fexceptions -frtti)

target_link_libraries(
    lexiq_onnx_security
    ${log-lib}
)
