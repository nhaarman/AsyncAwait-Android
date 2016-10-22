package com.nhaarman.async

import android.os.Handler
import android.os.Looper
import android.os.Message

var uiRunner: UIRunner = HandlerUIRunner()

internal fun runOnUi(action: () -> Unit) {
    uiRunner.runOnUi(action)
}

interface UIRunner {
    fun runOnUi(action: () -> Unit)
}

class SynchronousRunner : UIRunner {
    override fun runOnUi(action: () -> Unit) {
        action()
    }
}

class HandlerUIRunner : UIRunner {

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            (msg.obj as? (() -> Unit))?.invoke()
        }
    }

    override fun runOnUi(action: () -> Unit) {
        handler.obtainMessage(0, action).sendToTarget()
    }
}

