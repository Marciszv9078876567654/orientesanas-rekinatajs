# ML Kit discovers these classes from manifest metadata and invokes their no-arg
# constructors reflectively. R8 must retain those constructors in optimized APKs.
-keep class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
