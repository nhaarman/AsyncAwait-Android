package com.nhaarman.async

import android.os.Looper
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

class AsyncController<T>(private val returnToMainThread: Boolean = false) {

    internal val task = Task<T>()

    private fun isCanceled() = task.isCanceled()

    /**
     * Suspends the coroutine call until [task] completes.
     *
     * @return the result of [task].
     */
    suspend fun <R> await(task: Task<R>, machine: Continuation<R>) {
        if (isCanceled()) return
        this.task.awaitingOn(task)
        task.whenComplete(
              { result ->
                  this.task.awaitDone()
                  if (!isCanceled()) resume(machine, result)
              },
              { throwable ->
                  this.task.awaitDone()
                  if (!isCanceled()) resumeWithException(machine, throwable)
              }
        )
    }

    /**
     * For usage with Retrofit 2.
     * Enqueues [call] to be ran asynchronously and suspends the coroutine call until [call] completes.
     *
     * @return the result of [call].
     */
    suspend fun <R> await(call: Call<R>, machine: Continuation<Response<R>>) {
        if (isCanceled()) return
        task.awaitingOn(CancelableCall(call))
        call.enqueue(
              { response ->
                  task.awaitDone()
                  if (!isCanceled()) resume(machine, response)
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
