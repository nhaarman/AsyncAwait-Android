package com.nhaarman.async

import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Run asynchronous computations based on [c] coroutine parameter.
 *
 * Execution starts immediately within the 'async' call and it runs until
 * the first suspension point is reached (an 'await' call).
 * The remaining part of the coroutine will be executed as the awaited code
 * is completed.
 * There is no thread switching after 'await' calls.
 *
 * @param c a coroutine representing asynchronous computations
 * @param T the return type of the coroutine.
 *
 * @return Task object representing result of computations
 */
fun <T> async(coroutine c: AsyncController<T>.() -> Continuation<Unit>): Task<T> {
    val controller = AsyncController<T>()
    controller.c().resume(Unit)
    return controller.task
}

/**
 * Run asynchronous computations based on [c] coroutine parameter.
 *
 * Execution starts immediately within the 'async' call and it runs until
 * the first suspension point is reached (an 'await' call).
 * The remaining part of the coroutine will be executed as the awaited code
 * is completed.
 * There is no thread switching after 'await' calls.
 *
 * @param c a coroutine representing asynchronous computations
 *
 * @return Task object representing result of computations
 */
@JvmName("asyncWithoutParameter")
fun async(coroutine c: AsyncController<Unit>.() -> Continuation<Unit>): Task<Unit> {
    val controller = AsyncController<Unit>()
    controller.c().resume(Unit)
    return controller.task
}

/**
 * Run asynchronous computations based on [c] coroutine parameter.
 *
 * Execution starts immediately within the 'async' call and it runs until
 * the first suspension point is reached (an 'await' call).
 * The remaining part of the coroutine will be executed *on the main thread*
 * as the awaited code is completed.
 *
 * @param c a coroutine representing asynchronous computations
 *
 * @return Task object representing result of computations
 */
fun asyncUI(coroutine c: AsyncController<Unit>.() -> Continuation<Unit>): Task<Unit> {
    val controller = AsyncController<Unit>(returnToMainThread = true)
    controller.c().resume(Unit)
    return controller.task
}

/**
 * Returns a [Task] that completes after a specified time interval.
 *
 * @param time the delay to wait before completing the returned task.
 * @param unit the [TimeUnit] in which [time] is defined.
 *
 * @return a cancelable [Task].
 */
fun delay(time: Long, unit: TimeUnit): Task<Unit> {
    return Task<Unit>().apply {
        val task = this
        startedWith(executorService.submit {
            Thread.sleep(unit.toMillis(time))
            task.complete(Unit)
        })
    }
}

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

    private @Volatile var completedValue: T? = null
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

        completedValue?.let { onComplete(it) }
        erroredValue?.let { onError(it) }
    }

    internal fun startedWith(future: Future<*>) {
        runningTask = future
    }

    internal fun complete(value: T) {
        onComplete?.invoke(value)
        completedValue = value
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

        completedValue?.let { return it }
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

        completedValue?.let { return it }
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
}

class AsyncController<T>(private val returnToMainThread: Boolean = false) {

    internal val task = Task<T>()

    private fun isCanceled() = task.isCanceled()

    /**
     * Suspends the coroutine call until [c] completes.
     *
     * @return the result of [c].
     */
    suspend fun <R> await(c: Task<R>, machine: Continuation<R>) {
        if (isCanceled()) return
        task.awaitingOn(c)
        c.whenComplete(
              { result ->
                  task.awaitDone()
                  if (!isCanceled()) resume(machine, result)
              },
              { throwable ->
                  task.awaitDone()
                  if (!isCanceled()) resumeWithException(machine, throwable)
              }
        )
    }

    /**
     * Runs [f] asynchronously and suspends the coroutine call until [f] completes.
     *
     * @return the result of [f].
     */
    suspend fun <R> await(f: () -> R, machine: Continuation<R>) {
        if (isCanceled()) return
        task.startedWith(executorService.submit {
            try {
                val data = f()
                if (!isCanceled()) resume(machine, data)
            } catch(e: Throwable) {
                if (!isCanceled()) resumeWithException(machine, e)
            }
        })
    }

    private fun <R> resume(machine: Continuation<R>, result: R) {
        runOnUiIfNecessary { machine.resume(result) }
    }

    private fun <R> resumeWithException(machine: Continuation<R>, throwable: Throwable) {
        runOnUiIfNecessary { machine.resumeWithException(throwable) }
    }

    private fun runOnUiIfNecessary(action: () -> Unit) {
        if (returnToMainThread && Looper.myLooper() != Looper.getMainLooper()) {
            runOnUi(action)
        } else {
            action()
        }
    }

    @Suppress("unused", "unused_parameter")
    operator fun handleResult(value: T, c: Continuation<Nothing>) {
        task.complete(value)
    }

    @Suppress("unused", "unused_parameter")
    operator fun handleException(t: Throwable, c: Continuation<Nothing>) {
        if (isCanceled() && t is InterruptedException) return
        if (!task.handleError(t)) runOnUi { throw t }
    }
}

private val executorService by lazy {
    Executors.newCachedThreadPool()
}
