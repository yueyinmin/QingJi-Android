package com.qingji.app

import android.app.Application
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.SeedData
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App 的总管家。
 * 启动时先跑这里，适合放「全局只创建一次」的东西，比如数据库。
 * 以后接网络、接 AI 客户端也放这里。
 */
class QingJiApp : Application() {

    /** 懒加载：第一次用到时才真正打开数据库，启动更快。 */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()

        // --------------------------------------------------------
        // M0：第一次启动往数据库里灌假数据。
        // 为什么要放在后台线程：读写数据库是慢活，放在主线程会让
        // App 打开时卡一下，安卓甚至会直接报「界面无响应」。
        // 为什么要先判断：用户以后自己加了食物，重开 App 不能被覆盖。
        // --------------------------------------------------------
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 全新安装 → 全量灌入；老用户升级 → 只补「这次新加的食物」
                // 按名字去重，绝不覆盖用户自己录过的食物，也不会插出重复项。
                QingJiRepository(this@QingJiApp).ensurePresetFoods()
                QingJiRepository(this@QingJiApp).ensurePresetExercises()

                // v2.8：默认档案不再灌入——首次启动走引导页让用户填自己的数据。
            } catch (e: Exception) {
                // 以前这里是裸协程，一旦写库失败（磁盘满、数据库损坏）异常没人接，
                // 会直接抛到主线程把 App 干掉，表现就是「一打开就闪退」。
                // 现在吞掉异常：没有预置食物顶多是搜索列表空，不该让整个 App 起不来。
                // 搜索页发现食物库为空时，还留了一个「恢复预置食物库」的按钮兜底。
                android.util.Log.e("QingJiApp", "初始化预置数据失败", e)
            }
        }
    }
}
