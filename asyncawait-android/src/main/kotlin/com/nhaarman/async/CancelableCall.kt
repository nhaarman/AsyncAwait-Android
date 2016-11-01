package com.nhaarman.async

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class CancelableCall(
      private val call: Call<*>
) : Cancelable {

    override fun cancel() {
        call.cancel()
    }

    override fun isCanceled(): Boolean {
        return call.isCanceled
    }
}

inline fun <T> Call<T>.enqueue(
      crossinline onResponse: (Response<T>) -> Unit,
      crossinline onFailure: (Throwable) -> Unit
) =
      enqueue(object : Callback<T> {
          override fun onResponse(call: Call<T>, response: Response<T>) {
              onResponse(response)
          }

          override fun onFailure(call: Call<T>, t: Throwable) {
              onFailure(t)
          }
      })
