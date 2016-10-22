package com.nhaarman.async


interface Cancelable {

    /** Cancels the [Cancelable]. */
    fun cancel()

    /** Returns whether the [Cancelable] is canceled. */
    fun isCanceled(): Boolean
}

internal class CancelableImpl : Cancelable {

    private @Volatile var canceled = false

    override fun cancel() {
        canceled = true
    }

    override fun isCanceled(): Boolean {
        return canceled
    }
}
