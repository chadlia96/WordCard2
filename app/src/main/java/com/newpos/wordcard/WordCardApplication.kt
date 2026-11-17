package com.newpos.wordcard

import android.app.Application
import android.util.Log
import com.newpos.wordcard.data.AnkiDatabase
import kotlin.concurrent.thread

class WordCardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        thread(name = "database-initialization") {
            try {
                val database = AnkiDatabase.open(this)
                try {
                    // Room 延迟打开数据库；此处触发建库及同步的词库初始化回调。
                    database.openHelper.writableDatabase
                } finally {
                    database.close()
                }
            } catch (error: Exception) {
                // 建库事务失败会回滚，下次打开数据库时重新尝试。
                Log.e("DatabaseInitialization", "Database initialization failed", error)
            }
        }
    }
}
