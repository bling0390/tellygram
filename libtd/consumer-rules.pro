# The native library registers every bridge by name in JNI_OnLoad (RegisterNatives), and
# RegisterNatives aborts the process when a method is missing. -keepclasseswithmembernames
# only preserves names and still lets R8 shrink unused natives away, which killed the 1.2.0
# release build (clientExecute and five others); keep the class whole instead (2026-10-26).
-keep class org.drinkless.td.libcore.telegram.NativeClient { *; }
-keep class org.drinkless.td.libcore.telegram.TdApi$* { *; }
-keep class org.drinkless.td.libcore.telegram.TdApi { *; }