# Lotus AI —— release 混淆规则
# 保留 Compose / 序列化运行期所需的元数据。
-keepattributes *Annotation*, InnerClasses
-dontwarn org.jetbrains.annotations.**

# Shizuku UserService 由 Shizuku server 反射实例化，不能混淆类名和 Context 构造器。
-keep class ai.lotus.app.shizuku.LotusShizukuService { *; }
-keep class ai.lotus.app.shizuku.ILotusShizukuService$Stub { *; }
