package com.phongvv.musicplayermvp.data

import android.os.AsyncTask

class LocalAsyncTask<V, T>(
    private val callback: OnDataLocalCallback<T>,
    private val handler: (V) -> T,
) : AsyncTask<V, Unit, T>() {

    private var exception: Exception? = null

    override fun onPostExecute(result: T?) {
        super.onPostExecute(result)
        result?.let {
            callback.onSucceed(result)
        } ?: callback.onFailed(exception)
    }

    override fun doInBackground(vararg params: V): T? =
        try {
            handler(params[0])
        } catch (e: Exception) {
            exception = e
            null
        }
}
