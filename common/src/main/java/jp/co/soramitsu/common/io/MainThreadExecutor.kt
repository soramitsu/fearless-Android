package jp.co.soramitsu.common.io

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import java.util.concurrent.Executor

class MainThreadExecutor : Executor {
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    override fun execute(@NonNull command: Runnable) {
        mainThreadHandler.post(command)
    }
}