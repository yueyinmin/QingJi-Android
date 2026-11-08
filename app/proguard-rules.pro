# ===== 轻记 R8 规则（v2.7 起启用混淆）=====

# Room 实体与数据库
-keep class com.qingji.app.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase

# 网络层与序列化（AI 接口、JSON 解析靠反射读字段，不能混淆）
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.qingji.app.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 桌面小组件（系统反射实例化 Receiver，不能混淆）
-keep class com.qingji.app.widget.** { *; }

# ZXing 条码库
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
