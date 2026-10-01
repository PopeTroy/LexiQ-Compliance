#include <jni.h>
#include <string>
#include <vector>
#include "onnxruntime_cxx_api.h"

Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "LexiQOnnxEngine");
Ort::Session* session = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_org_linguistic_assistant_OnnxEngine_initModel(JNIEnv* env_ptr, jobject thiz, jstring model_path) {
    const char* path = env_ptr->GetStringUTFChars(model_path, nullptr);
    
    Ort::SessionOptions session_options;
    // Enable NNAPI Execution Provider for hardware acceleration on mobile NPUs/GPUs
    #ifdef USE_NNAPI
    Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_Nnapi(session_options, 0));
    #endif
    
    session = new Ort::Session(env, path, session_options);
    env_ptr->ReleaseStringUTFChars(model_path, path);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_linguistic_assistant_OnnxEngine_evaluateBehavior(JNIEnv* env_ptr, jobject thiz, jfloatArray features) {
    jfloat* input_data = env_ptr->GetFloatElements(features, nullptr);
    jsize input_len = env_ptr->GetArrayLength(features);

    std::vector<int64_t> input_shape = {1, input_len};
    auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    Ort::Value input_tensor = Ort::Value::CreateTensor<float>(
        memory_info, input_data, input_len, input_shape.data(), input_shape.size()
    );

    const char* input_names[] = {"input_features"};
    const char* output_names[] = {"behavior_embedding"};

    auto output_tensors = session->Run(
        Ort::RunOptions{nullptr}, input_names, &input_tensor, 1, output_names, 1
    );

    float* float_array = output_tensors[0].GetTensorMutableData<float>();
    size_t output_len = output_tensors[0].GetTensorTypeAndShapeInfo().GetElementCount();

    jfloatArray result = env_ptr->NewFloatArray(output_len);
    env_ptr->SetFloatArrayRegion(result, 0, output_len, float_array);

    env_ptr->ReleaseFloatElements(features, input_data, JNI_ABORT);
    return result;
}
