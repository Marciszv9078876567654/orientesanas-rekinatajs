# ML Kit discovers these classes from manifest metadata and invokes their no-arg
# constructors reflectively. R8 must retain those constructors in optimized APKs.
-keep class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

# OpenCV's Java bridge binds native methods to libopencv_java*.so by name.
# Keep this scoped safety net even though Android's default rules also protect JNI,
# so removing the default rules accidentally cannot break this dependency's bridge.
-keepclasseswithmembernames class org.opencv.** {
    native <methods>;
}
