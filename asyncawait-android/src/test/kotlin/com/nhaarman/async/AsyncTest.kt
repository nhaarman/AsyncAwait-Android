package com.nhaarman.async

import com.nhaarman.expect.expect
import com.nhaarman.mockito_kotlin.*
import io.reactivex.disposables.Disposable
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import rx.Subscription
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import io.reactivex.Single as Single2
import rx.Single as Single1

@Suppress("IllegalIdentifier")
class AsyncTest {

    private val resultString = "Result"

    private val mainThread = Thread.currentThread()

    @Before
    fun setup() {
        uiRunner = SynchronousRunner()
    }

    @Test
    fun `waiting on a directly terminating non-parameterized async returns the result`() {
        /* When */
        val result = async { Unit }.testWait()

        /* Then */
        expect(result).toBe(Unit)
    }

    @Test
    fun `waiting on a directly terminating async returns the result`() {
        /* When */
        val result = async<String> { resultString }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `waiting on a task that directly completes with a null value`() {
        /* When */
        val result = async<String?> { null }.testWait()

        /* Then */
        expect(result).toBeNull()
    }

    @Test
    fun `waiting on an async that sleeps still returns the result`() {
        /* When */
        val result = async<String> { Thread.sleep(100); resultString }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `waiting on a task that sleeps still returns the null value`() {
        /* When */
        val result = async<String?> { Thread.sleep(100); null }.testWait()

        /* Then */
        expect(result).toBeNull()
    }

    @Test
    fun `code executed in an async continues on mainthread until await`() {
        /* When */
        val result = async<String> {
            expect(Thread.currentThread()).toBeTheSameAs(mainThread)
            await { expect(Thread.currentThread().name).toContain("pool") }
            expect(Thread.currentThread().name).toContain("pool")

            resultString
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test(expected = IllegalStateException::class)
    fun `throwing an error synchronously inside async propagates`() {
        /* When */
        async<String> {
            error("Expected")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `throwing an error after await propagates when waited on`() {
        /* When */
        async<String> {
            await {}
            error("Expected")
        }.testWait()
    }

    @Test
    fun `catching an exception inside async does not propagate exception`() {
        /* When */
        val result = async<String> {
            try {
                await<String> { error("Error") }
            } catch(e: IllegalStateException) {
                resultString
            }
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `catching an exception outside async does not propagate exception`() {
        /* When */
        val result = try {
            async<String> {
                await<String> { error("Error") }
            }.testWait()
        } catch(e: IllegalStateException) {
            resultString
        }

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test(expected = IllegalStateException::class)
    fun `waiting on an already canceled task throws an exception`() {
        /* Given */
        val task = async<String> {
            await { while (true); }
            resultString
        }
        task.cancel()

        /* When */
        task.testWait()
    }

    @Test
    fun `awaiting on a task`() {
        /* When */
        val result = async<String> {
            await(async<String> { resultString })
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test(expected = IllegalStateException::class)
    fun `awaiting on a task that errors`() {
        /* When */
        async<String> {
            await(async<String> { error("Expected") })
        }.testWait()
    }

    @Test(expected = IllegalStateException::class)
    fun `awaiting on a task that errors asynchronously`() {
        /* When */
        async<String> {
            await(async<String> {
                await<String> { error("Expected") }
            })
        }.testWait()
    }

    @Test
    fun `awaiting on a delegated task`() {
        /* When */
        val result = async<String> {
            val a by async<String> { resultString }
            a
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `awaiting on a delegated lambda`() {
        /* When */
        val result = async<String> {
            val a by { resultString }
            a
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test(expected = IllegalStateException::class)
    fun `awaiting on a delegated task that errors`() {
        /* When */
        async<String> {
            val a by async<String> { error("Expected") }
            a
        }.testWait()
    }

    @Test
    fun `delegation evaluates in order`() {
        /* Given */
        val a = mock<() -> Unit>()
        val b = mock<() -> Unit>()

        /* When */
        async {
            val aResult by async { a() }
            val bResult by async { b() }

            bResult
            aResult
        }

        /* Then */
        inOrder(a, b) {
            verify(a).invoke()
            verify(b).invoke()
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `awaiting on a retrofit call`() {
        val call = mock<Call<String>>()
        whenever(call.enqueue(any())).then {
            (it.arguments[0] as Callback<String>).onResponse(call, Response.success(resultString))
        }

        /* When */
        val result = async<String> {
            await(call).body()
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `canceling a retrofit call`() {
        /* Given */
        val call = mock<Call<String>>()
        val task = async<String> {
            await(call).body()
        }

        /* When */
        task.cancel()

        /* Then */
        verify(call).cancel()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `awaiting on a delegated retrofit call`() {
        val call = mock<Call<String>>()
        whenever(call.enqueue(any())).then {
            (it.arguments[0] as Callback<String>).onResponse(call, Response.success(resultString))
        }

        /* When */
        val result = async<String> {
            val a by call
            a.body()
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `awaiting on an RxJava1 Single`() {
        /* When */
        val result = async<String> {
            await(Single1.just(resultString))
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `canceling an RxJava1 Single`() {
        /* Given */
        val subscription = mock<Subscription>()
        val single = mock<Single1<String>> {
            on { subscribe(any(), any()) } doReturn subscription
        }

        val task = async<String> {
            await(single)
        }

        /* When */
        task.cancel()

        /* Then */
        verify(subscription).unsubscribe()
    }

    @Test
    fun `awaiting on a delegated RxJava1 Single`() {
        /* When */
        val result = async<String> {
            val a by Single1.just(resultString)
            a
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `awaiting on an RxJava2 Single`() {
        /* When */
        val result = async<String> {
            await(Single2.just(resultString))
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun `canceling an RxJava2 Single`() {
        /* Given */
        val disposable = mock<Disposable>()
        val single = mock<Single2<String>> {
            on { subscribe(any(), any()) } doReturn disposable
        }

        val task = async<String> {
            await(single)
        }

        /* When */
        task.cancel()

        /* Then */
        verify(disposable).dispose()
    }

    @Test
    fun `awaiting on a delegated RxJava2 Single`() {
        /* When */
        val result = async<String> {
            val a by Single2.just(resultString)
            a
        }.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test
    fun delay() {
        /* Given */
        val start = System.currentTimeMillis()

        /* When */
        delay(100, MILLISECONDS).testWait()

        /* Then */
        expect(System.currentTimeMillis() - start).toBeGreaterThan(99)
    }

    @Test
    fun completedTask() {
        /* Given */
        val task = Task.completed(resultString)

        /* When */
        val result = task.testWait()

        /* Then */
        expect(result).toBe(resultString)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun erroredTask() {
        /* Given */
        val task = Task.errored<String>(UnsupportedOperationException())

        /* When */
        task.testWait()
    }
}

private fun <T> Task<T>.testWait() = wait(1, SECONDS)