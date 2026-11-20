-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**

-keep class io.flutter.** { *; }
-keep class io.flutter.embedding.** { *; }

-keepattributes Exceptions,InnerClasses,Signature,LineNumberTable,SourceFile,*Annotation*
