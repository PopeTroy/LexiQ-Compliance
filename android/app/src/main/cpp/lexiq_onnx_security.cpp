#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <android/log.h>

#if defined(__has_include)
#if __has_include("onnxruntime_cxx_api.h")
#include "onnxruntime_cxx_api.h"
#define LEXIQ_HAS_ORT_CXX_API 1
#endif
#endif

#ifndef LEXIQ_HAS_ORT_CXX_API
#define LEXIQ_HAS_ORT_CXX_API 0
#endif

#define LOG_TAG "LexiQOnnxSecurity"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_engine_mutex;

#if LEXIQ_HAS_ORT_CXX_API
Ort::Env g_ort_env(ORT_LOGGING_LEVEL_WARNING, "LexiQOnnxEngine");
std::unique_ptr<Ort::Session> g_session;

void configure_nnapi_if_enabled(Ort::SessionOptions& session_options) {
#if defined(USE_NNAPI)
    try {
#if defined(ORT_API_VERSION) && (ORT_API_VERSION >= 14)
        Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_Nnapi_V2(session_options, 0));
#elif defined(ORT_API_VERSION)
        Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_Nnapi(session_options, 0));
#endif
    } catch (...) {
        LOGW("NNAPI execution provider unavailable; falling back to default CPU provider");
    }
#else
    (void)session_options;
#endif
}
#else
std::string g_loaded_model_path;
bool g_session_initialized = false;
#endif

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_org_linguistic_assistant_OnnxEngine_initModel(JNIEnv* env_ptr, jobject thiz, jstring model_path) {
    (void)thiz;
    if (env_ptr == nullptr || model_path == nullptr) {
        return JNI_FALSE;
    }

    const char* path = env_ptr->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr || path[0] == '\0') {
        if (path != nullptr) {
            env_ptr->ReleaseStringUTFChars(model_path, path);
        }
        return JNI_FALSE;
    }

    std::string model_path_str(path);
    env_ptr->ReleaseStringUTFChars(model_path, path);

#if LEXIQ_HAS_ORT_CXX_API
    try {
        Ort::SessionOptions session_options;
        session_options.SetIntraOpNumThreads(2);
        session_options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        configure_nnapi_if_enabled(session_options);

        auto new_session = std::make_unique<Ort::Session>(g_ort_env, model_path_str.c_str(), session_options);

        std::lock_guard<std::mutex> lock(g_engine_mutex);
        g_session = std::move(new_session);
        LOGI("ONNX session initialized for: %s", model_path_str.c_str());
        return JNI_TRUE;
    } catch (...) {
        LOGW("Failed to initialize ONNX session for: %s", model_path_str.c_str());
        return JNI_FALSE;
    }
#else
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    g_loaded_model_path = model_path_str;
    g_session_initialized = true;
    LOGI("JNI fallback session registered (no C++ ORT header): %s", g_loaded_model_path.c_str());
    return JNI_TRUE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_org_linguistic_assistant_OnnxEngine_destroyModel(JNIEnv* env_ptr, jobject thiz) {
    (void)env_ptr;
    (void)thiz;
    std::lock_guard<std::mutex> lock(g_engine_mutex);
#if LEXIQ_HAS_ORT_CXX_API
    g_session.reset();
#else
    g_loaded_model_path.clear();
    g_session_initialized = false;
#endif
    LOGI("ONNX engine session destroyed");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_linguistic_assistant_OnnxEngine_evaluateBehavior(JNIEnv* env_ptr, jobject thiz, jfloatArray features) {
    (void)thiz;
    if (env_ptr == nullptr || features == nullptr) {
        return nullptr;
    }

    const jsize input_len = env_ptr->GetArrayLength(features);
    if (input_len <= 0) {
        return nullptr;
    }

    std::vector<float> host_input(static_cast<size_t>(input_len));
    env_ptr->GetFloatArrayRegion(features, 0, input_len, host_input.data());
    if (env_ptr->ExceptionCheck()) {
        return nullptr;
    }

#if LEXIQ_HAS_ORT_CXX_API
    try {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        if (!g_session) {
            return nullptr;
        }

        const std::vector<int64_t> input_shape = {1, static_cast<int64_t>(input_len)};
        const auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

        Ort::Value input_tensor = Ort::Value::CreateTensor<float>(
            memory_info,
            host_input.data(),
            host_input.size(),
            input_shape.data(),
            input_shape.size()
        );

        const char* input_names[] = {"input_features"};
        const char* output_names[] = {"behavior_embedding"};

        auto output_tensors = g_session->Run(
            Ort::RunOptions{nullptr},
            input_names,
            &input_tensor,
            1,
            output_names,
            1
        );

        if (output_tensors.empty() || !output_tensors[0].IsTensor()) {
            return nullptr;
        }

        const float* float_array = output_tensors[0].GetTensorData<float>();
        const size_t output_len = output_tensors[0].GetTensorTypeAndShapeInfo().GetElementCount();
        if (float_array == nullptr || output_len == 0) {
            return nullptr;
        }

        const jsize out_size = static_cast<jsize>(std::min<size_t>(output_len, 1024 * 1024));
        jfloatArray result = env_ptr->NewFloatArray(out_size);
        if (result == nullptr) {
            return nullptr;
        }
        env_ptr->SetFloatArrayRegion(result, 0, out_size, float_array);
        return result;
    } catch (...) {
        return nullptr;
    }
#else
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        if (!g_session_initialized) {
            return nullptr;
        }
    }

    // Pass-through fallback when C++ ORT headers are not linked into native CMake
    const jsize out_size = static_cast<jsize>(std::min<size_t>(host_input.size(), 1024 * 1024));
    jfloatArray result = env_ptr->NewFloatArray(out_size);
    if (result == nullptr) {
        return nullptr;
    }
    env_ptr->SetFloatArrayRegion(result, 0, out_size, host_input.data());
    return result;
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_linguistic_assistant_OnnxEngine_evaluateBehaviorInt8(
    JNIEnv* env_ptr,
    jobject thiz,
    jbyteArray quantized_features
) {
    (void)thiz;
    if (env_ptr == nullptr || quantized_features == nullptr) {
        return nullptr;
    }

    const jsize input_len = env_ptr->GetArrayLength(quantized_features);
    if (input_len <= 0) {
        return nullptr;
    }

    std::vector<int8_t> host_input(static_cast<size_t>(input_len));
    env_ptr->GetByteArrayRegion(
        quantized_features,
        0,
        input_len,
        reinterpret_cast<jbyte*>(host_input.data())
    );
    if (env_ptr->ExceptionCheck()) {
        return nullptr;
    }

#if LEXIQ_HAS_ORT_CXX_API
    try {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        if (!g_session) {
            return nullptr;
        }

        const std::vector<int64_t> input_shape = {1, static_cast<int64_t>(input_len)};
        const auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

        Ort::Value input_tensor = Ort::Value::CreateTensor<int8_t>(
            memory_info,
            host_input.data(),
            host_input.size(),
            input_shape.data(),
            input_shape.size()
        );

        const char* input_names[] = {"input_features_int8"};
        const char* output_names[] = {"behavior_embedding_int8"};

        auto output_tensors = g_session->Run(
            Ort::RunOptions{nullptr},
            input_names,
            &input_tensor,
            1,
            output_names,
            1
        );

        if (output_tensors.empty() || !output_tensors[0].IsTensor()) {
            return nullptr;
        }

        const int8_t* int8_array = output_tensors[0].GetTensorData<int8_t>();
        const size_t output_len = output_tensors[0].GetTensorTypeAndShapeInfo().GetElementCount();
        if (int8_array == nullptr || output_len == 0) {
            return nullptr;
        }

        const jsize out_size = static_cast<jsize>(std::min<size_t>(output_len, 1024 * 1024));
        jbyteArray result = env_ptr->NewByteArray(out_size);
        if (result == nullptr) {
            return nullptr;
        }
        env_ptr->SetByteArrayRegion(
            result,
            0,
            out_size,
            reinterpret_cast<const jbyte*>(int8_array)
        );
        return result;
    } catch (...) {
        return nullptr;
    }
#else
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        if (!g_session_initialized) {
            return nullptr;
        }
    }

    const jsize out_size = static_cast<jsize>(std::min<size_t>(host_input.size(), 1024 * 1024));
    jbyteArray result = env_ptr->NewByteArray(out_size);
    if (result == nullptr) {
        return nullptr;
    }
    env_ptr->SetByteArrayRegion(
        result,
        0,
        out_size,
        reinterpret_cast<const jbyte*>(host_input.data())
    );
    return result;
#endif
}
