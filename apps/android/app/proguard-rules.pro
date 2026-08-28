-keep class com.junchen.jingdu.NativeCore { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Hosted Macrobenchmark-only entry point. The class exists only in src/benchmark, so this
# keep rule cannot add benchmark controls to the production release source set.
-keep class com.junchen.jingdu.ReaderBenchmarkFixtureProvider { *; }
