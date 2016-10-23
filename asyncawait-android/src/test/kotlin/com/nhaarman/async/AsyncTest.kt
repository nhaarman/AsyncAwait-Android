package com.nhaarman.async

import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

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
    fun `waiting on an async that sleeps still returns the result`() {
        /* When */
        val result = async<String> { Thread.sleep(100); resultString }.testWait()

        /* Then */
        expect(result).toBe(resultString)
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