package com.nhaarman.async

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


/**
 * A class that represents a cancelable task that can be awaited on.
 * When the task completes normally, an instance of type [T] is passed
 * as the result to the 'await' call.
 */
class Task<T>(
      private val cancelable: Cancelable = CancelableImpl()
) : Cancelable by cancelable {

    /** The Future that was created to start the coroutine. */
    private var runningTask: Future<*>? = null

    private @Volatile var onComplete: ((T) -> Unit)? = null
    private @Volatile var onError: ((Throwable) -> Unit)? = null

    private @Volatile var completedValue: CompletedValue<T>? = null
    private @Volatile var erroredValue: Throwable? = null

    /** The task that we're currently awaiting on. */
    private var current: Cancelable? = null

    internal fun awaitingOn(cancelable: Cancelable) {
        if (current != null) error("Already waiting on $current.")
        current = cancelable
    }

    internal fun awaitDone() {
        current = null
    }

    private val latch = CountDownLatch(1)

    internal fun whenComplete(onComplete: (T) -> Unit, onError: (Throwable) -> Unit) {
        this.onComplete = onComplete
        this.onError = onError

        completedValue?.let { onComplete(it.value) }
        erroredValue?.let { onError(it) }
    }

    internal fun startedWith(future: Future<*>) {
        runningTask = future
    }

    internal fun complete(value: T) {
        onComplete?.invoke(value)
        completedValue = CompletedValue(value)
        latch.countDown()
    }

    internal fun handleError(t: Throwable): Boolean {
        val result = onError?.invoke(t) != null
        erroredValue = t
        latch.countDown()
        return result
    }

    override fun cancel() {
        cancelable.cancel()

        current?.cancel()
        current = null

        runningTask?.cancel(true)
        runningTask = null

        onComplete = null
        onError = null
    }

    /**
     * Blocks the current thread until this task has completed
     * either successfully or unsuccessfully.
     *
     * @throws IllegalStateException if the task was already canceled.
     */
    fun wait(): T {
        if (isCanceled()) throw IllegalStateException("Task is canceled.")
        latch.await()

        completedValue?.let { return it.value }
        erroredValue?.let { throw it }

        error("Neither completed nor errored value set.")
    }

    /**
     * Blocks the current thread until this task has completed
     * either successfully or unsuccessfully, or a timeout has passed.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of [timeout]
     *
     * @throws TimeoutException if the timeout has passed before the task was completed.
     * @throws IllegalStateException if the task was already canceled.
     */
    fun wait(timeout: Long, unit: TimeUnit): T {
        if (isCanceled()) throw IllegalStateException("Task is canceled.")

        latch.await(timeout, unit)

        if (latch.count > 0) throw TimeoutException()

        completedValue?.let { return it.value }
        erroredValue?.let { throw it }

        error("Neither completed nor errored value set.")
    }

    companion object {

        /**
         * Creates a [Task] that is completed with value [t].
         */
        @JvmStatic
        fun <T> completed(t: T) = Task<T>().apply { complete(t) }

        /**
         * Creates a [Task] that has errored with [t].
         */
        @JvmStatic
        fun <T> errored(t: Throwable) = Task<T>().apply { handleError(t) }
    }

    private class CompletedValue<T>(val value: T)
}
