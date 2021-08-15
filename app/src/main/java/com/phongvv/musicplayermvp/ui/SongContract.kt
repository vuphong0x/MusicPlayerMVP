package com.phongvv.musicplayermvp.ui

import com.phongvv.musicplayermvp.base.BasePresenter
import com.phongvv.musicplayermvp.models.Song

interface SongContract {
    interface View {
        fun showError(error: String)
        fun showAllSongs(songs: List<Song>)
    }

    interface Presenter : BasePresenter {
        fun getAllSongs()
    }
}
