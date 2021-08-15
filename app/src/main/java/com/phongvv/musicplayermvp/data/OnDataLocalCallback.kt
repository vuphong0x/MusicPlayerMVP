package com.phongvv.musicplayermvp.data

interface OnDataLocalCallback<T> {
    fun onSucceed(data: T)
    fun onFailed(e: Exception?)
}
