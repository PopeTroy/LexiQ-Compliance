package org.linguistic.assistant

class OnnxEngine {
    external fun initModel(modelPath: String)
    external fun evaluateBehavior(features: FloatArray): FloatArray

    companion object {
        init {
            System.loadLibrary("lexiq_onnx_native")
        }
    }
}
