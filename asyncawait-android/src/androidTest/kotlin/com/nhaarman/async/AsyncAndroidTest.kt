package com.nhaarman.async

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class AsyncAndroidTest {

    private val resultString = "result"

    @Test
    fun returnToMainThread_fromAwait() {
        /* Given */
        var a: String? = null

        /* When */
        asyncUI {
            a = await {
                expect(Looper.myLooper()).toNotBeTheSameAs(Looper.getMainLooper())
                resultString
            }
            /* Then */
            expect(Looper.myLooper()).toBeTheSameAs(Looper.getMainLooper())
        }.wait()

        /* Then */
        expect(a).toBe(resultString)
    }
}

