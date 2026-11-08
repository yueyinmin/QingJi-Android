# 保持这些类不被混淆（接 AI 接口、写 JSON 解析时用得上）
-keep class com.qingji.app.data.local.entity.** { *; }

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase
