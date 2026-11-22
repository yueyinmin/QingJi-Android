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

# ViewModel 由 androidx.lifecycle 通过反射按构造函数创建，类名与 (Application)/(SavedStateHandle)
# 构造函数签名都不能改。曾踩坑：日记隐私锁的 DiaryPrivacyViewModel(Application) 被 R8 重命名后，
# by viewModels() 反射找不到构造函数，覆盖安装后启动即闪退（NoSuchMethodException <init>[Application]）。
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}

# ZXing 条码库
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
