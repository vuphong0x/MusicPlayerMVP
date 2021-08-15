package com.phongvv.musicplayermvp.models

data class Song(
    val title: String,
    val duration: Int,
    val path: String?,
    val albumName: String,
    val artistName: String
)

