package com.dskja.betterstreamflix.models.sololatino

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val file_id: Int = 0,
    val video_language: String = "",
    val sortedEmbeds: List<Embed> = emptyList(),
)

@Serializable
data class Embed(
    val servername: String = "",
    val link: String = "",
    val type: String = "",
)