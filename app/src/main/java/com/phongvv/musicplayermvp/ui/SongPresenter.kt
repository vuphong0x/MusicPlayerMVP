package com.phongvv.musicplayermvp.ui

import com.phongvv.musicplayermvp.data.OnDataLocalCallback
import com.phongvv.musicplayermvp.data.SongRepository
import com.phongvv.musicplayermvp.models.Song

class SongPresenter(
    private val view: SongContract.View,
    private val repository: SongRepository
) : SongContract.Presenter {

    override fun start() {
        getAllSongs()
    }

    override fun getAllSongs() {
        repository.getAllSong(object : OnDataLocalCallback<List<Song>> {
            override fun onSucceed(data: List<Song>) {
                view.showAllSongs(data)
            }

            override fun onFailed(e: Exception?) {
                view.showError(e?.message.toString())
            }
        })
    }
}
