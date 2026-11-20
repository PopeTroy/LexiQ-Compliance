cmake_minimum_required(VERSION 3.22.1)
project("lexiq_onnx_security" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ORT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/third_party/onnxruntime")
set(ORT_INCLUDE_DIR "${ORT_ROOT}/headers")
set(ORT_LIB_FILE "${ORT_ROOT}/jni/${ANDROID_ABI}/libonnxruntime.so")

add_library(
    lexiq_onnx_security
    SHARED
    lexiq_onnx_security.cpp
)

target_include_directories(
    lexiq_onnx_security
    PRIVATE
    "${ORT_INCLUDE_DIR}"
)

option(USE_NNAPI "Enable ONNX Runtime NNAPI Execution Provider" ON)
if(USE_NNAPI)
    target_compile_definitions(lexiq_onnx_security PRIVATE USE_NNAPI)
endif()

find_library(log-lib log)

target_link_libraries(
    lexiq_onnx_security
    PRIVATE
    "${ORT_LIB_FILE}"
    ${log-lib}
)
