package com.example.dsh.adapter

import android.os.Build
import android.util.Log
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter
import com.example.dsh.BuildConfig

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            throw throwable
        } else {
            Log.e(TAG, "KR error: ${throwable.stackTraceToString()}")
            // 保留最近一次崩溃栈，下次启动由页面读取并写入日志中心
            runCatching {
                val app = com.example.dsh.KRApplication.application
                val info = buildString {
                    appendLine("time=${System.currentTimeMillis()}")
                    appendLine("version=${BuildConfig.VERSION_NAME}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}")
                    appendLine("thread=${Thread.currentThread().name}")
                    append(throwable.stackTraceToString())
                }
                java.io.File(app.filesDir, "last_crash.txt").writeText(info)
            }
        }
    }

}