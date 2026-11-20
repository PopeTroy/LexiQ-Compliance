# ONNX Runtime / Native JNI Preservation
-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**

# Flutter / Dart Reflection
-keep class io.flutter.** { *; }
-keep class io.flutter.embedding.** { *; }

# Retain debug/line attributes for native/JNI and stack diagnostics
-keepattributes Exceptions,InnerClasses,Signature,LineNumberTable,SourceFile,*Annotation*
