package com.phongvv.musicplayermvp.data

import com.phongvv.musicplayermvp.models.Song

class SongRepository private constructor(
    private val local: SongDataSource
) : SongDataSource {

    override fun getAllSong(callback: OnDataLocalCallback<List<Song>>) {
        local.getAllSong(callback)
    }

    companion object {
        private var instance: SongRepository? = null
        fun getInstance(local: SongDataSource) =
            instance ?: SongRepository(local).also { instance = it }
    }
}
