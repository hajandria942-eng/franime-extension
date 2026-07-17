package eu.kanade.tachiyomi.animeextension.fr.franime.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val id: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String,
    @SerialName("titles_alt") val titlesAlt: TitlesAlt = TitlesAlt(),
    val poster: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val note: Double = 0.0,
    val seasons: List<Season> = emptyList()
)

@Serializable
data class TitlesAlt(
    val en: String? = null,
    @SerialName("en_jp") val enJp: String? = null,
    @SerialName("ja_jp") val jaJp: String? = null
)

@Serializable
data class Season(
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class Episode(
    val title: String? = null,
    val languages: Languages = Languages()
)

@Serializable
data class Languages(
    val vo: LanguagePlayers = LanguagePlayers(),
    val vf: LanguagePlayers = LanguagePlayers()
)

@Serializable
data class LanguagePlayers(
    val players: List<String> = emptyList()
)
