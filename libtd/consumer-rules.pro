# TDLib's native library reaches back into Java by name: JNI_OnLoad registers every bridge
# through RegisterNatives, and jni::init_vars resolves classes with FindClass. Both abort the
# process when a name is missing or renamed. Keep the whole binding intact — one rule, so no
# single class can be forgotten (2026-10-26: 1.2.0 lost the native methods, 1.2.1 lost Client).
-keep class org.drinkless.td.libcore.telegram.** { *; }
