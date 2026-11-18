# R8 в выпуске нужен ровно для одного: убрать неиспользуемый клиент рекламного идентификатора
# (com.google.android.gms.ads.identifier) из play-services-basement 10.2.0. Его тянет play-services-wearable 10.2.0,
# а RuStore по имени пакета принимает его за рекламный SDK Google Mobile Ads (AdMob).
# Всё остальное остаётся как есть: без переименования и оптимизаций, все прочие классы и члены сохраняются
# (Gson читает поля моделей и DTO часов по именам, iText и Room — через отражение).
# Предупреждения о классах, которых нет на Android (java.awt, java.beans, Jackson), — в proguard-missing.pro.
-dontobfuscate
-dontoptimize
-keepattributes *
-keep class !com.google.android.gms.ads.identifier.**, ** { *; }
-keep interface !com.google.android.gms.ads.identifier.**, ** { *; }
-keep enum !com.google.android.gms.ads.identifier.**, ** { *; }
