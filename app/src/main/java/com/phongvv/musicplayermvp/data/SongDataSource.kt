package com.phongvv.musicplayermvp.data

import com.phongvv.musicplayermvp.models.Song

interface SongDataSource {
    fun getAllSong(callback: OnDataLocalCallback<List<Song>>)
}

