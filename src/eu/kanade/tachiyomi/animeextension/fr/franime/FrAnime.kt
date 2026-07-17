package eu.kanade.tachiyomi.animeextension.fr.franime

import aniyomi.lib.sendvidextractor.SendvidExtractor
import aniyomi.lib.sibnetextractor.SibnetExtractor
import aniyomi.lib.vidmolyextractor.VidMolyExtractor
import aniyomi.lib.vkextractor.VkExtractor
import eu.kanade.tachiyomi.animeextension.fr.franime.dto.Anime
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import keiyoushi.utils.parallelCatchingFlatMap
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class FrAnime : AnimeHttpSource() {

    override val name = "FRAnime"
    private val domain = "franime.fr"
    override val baseUrl = "https://$domain"
    private val baseApiUrl = "https://api.$domain/api"
    private val baseApiAnimeUrl = "$baseApiUrl/anime"

    override val lang = "fr"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    private val json: Json by injectLazy()

    // Database caching (30 minutes)
    private var lastDatabaseFetch: Long = 0
    private var cachedDatabase: List<Anime>? = null
    private val cacheValidityDuration = TimeUnit.MINUTES.toMillis(30)

    private suspend fun getDatabase(): List<Anime> {
        val currentTime = System.currentTimeMillis()
        if (cachedDatabase != null && (currentTime - lastDatabaseFetch) < cacheValidityDuration) {
            return cachedDatabase!!
        }
        return try {
            val response = client.newCall(GET("$baseApiUrl/animes/", headers)).await()
            val bodyString = response.body.string()
            val database = json.decodeFromString<List<Anime>>(bodyString)
            cachedDatabase = database
            lastDatabaseFetch = currentTime
            database
        } catch (e: Exception) {
            cachedDatabase ?: throw e
        }
    }

    // Popular
    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val db = getDatabase()
        return pagesToAnimesPage(db.sortedByDescending { it.note }, page)
    }
    override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()

    // Latest
    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val db = getDatabase()
        return pagesToAnimesPage(db.reversed(), page)
    }
    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    // Search
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val db = getDatabase()
        if (query.startsWith("https://") || query.startsWith("http://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported URL: please use a $domain URL")
            }
            val id = url.pathSegments.getOrNull(1) ?: throw Exception("Invalid anime URL format")
            return getSearchAnime(page, id, filters)
        }
        val results = db.filter { anime ->
            anime.title.contains(query, true) ||
            anime.originalTitle.contains(query, true) ||
            anime.titlesAlt.en?.contains(query, true) == true ||
            anime.titlesAlt.enJp?.contains(query, true) == true ||
            anime.titlesAlt.jaJp?.contains(query, true) == true ||
            titleToUrl(anime.originalTitle).contains(query.lowercase())
        }
        return pagesToAnimesPage(results, page)
    }
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = throw UnsupportedOperationException()

    // Details
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()

    // Episodes
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val db = getDatabase()
        val url = (baseUrl + anime.url).toHttpUrl()
        val stem = url.encodedPathSegments.last()
        val language = url.queryParameter("lang") ?: "vo"
        val season = url.queryParameter("s")?.toIntOrNull() ?: 1
        val animeData = db.first { titleToUrl(it.originalTitle) == stem }
        return animeData.seasons[season - 1].episodes
            .mapIndexedNotNull { index, ep ->
                val players = when (language) {
                    "vo" -> ep.languages.vo.players
                    else -> ep.languages.vf.players
                }
                if (players.isEmpty()) return@mapIndexedNotNull null
                SEpisode.create().apply {
                    setUrlWithoutDomain(anime.url + "&ep=${index + 1}")
                    name = ep.title ?: "Episode ${index + 1}"
                    episode_number = (index + 1).toFloat()
                }
            }
            .sortedByDescending { it.episode_number }
    }
    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    // Videos
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val db = getDatabase()
        val url = (baseUrl + episode.url).toHttpUrl()
        val seasonNumber = url.queryParameter("s")?.toIntOrNull() ?: 1
        val episodeNumber = url.queryParameter("ep")?.toIntOrNull() ?: 1
        val episodeLang = url.queryParameter("lang") ?: "vo"
        val stem = url.encodedPathSegments.last()
        val animeData = db.first { titleToUrl(it.originalTitle) == stem }
        val episodeData = animeData.seasons[seasonNumber - 1].episodes[episodeNumber - 1]
        val videoBaseUrl = "$baseApiAnimeUrl/${animeData.id}/${seasonNumber - 1}/${episodeNumber - 1}"

        val players = if (episodeLang == "vo") episodeData.languages.vo.players
                      else episodeData.languages.vf.players

        val sendvidExtractor by lazy { SendvidExtractor(client, headers) }
        val sibnetExtractor by lazy { SibnetExtractor(client) }
        val vkExtractor by lazy { VkExtractor(client, headers) }
        val vidMolyExtractor by lazy { VidMolyExtractor(client, headers) }

        return players.withIndex().parallelCatchingFlatMap { (index, playerName) ->
            try {
                val apiUrl = "$videoBaseUrl/$episodeLang/$index"
                val playerUrl = client.newCall(GET(apiUrl, headers)).await().body.string()
                when (playerName.lowercase()) {
                    "sendvid" -> sendvidExtractor.videosFromUrl(playerUrl)
                    "sibnet" -> sibnetExtractor.videosFromUrl(playerUrl)
                    "vk" -> vkExtractor.videosFromUrl(playerUrl)
                    "vidmoly" -> vidMolyExtractor.videosFromUrl(playerUrl)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    // Utilities
    private fun pagesToAnimesPage(pages: List<Anime>, page: Int): AnimesPage {
        val chunks = pages.chunked(50)
        val hasNextPage = chunks.size > page
        val entries = pageToSAnimes(chunks.getOrNull(page - 1) ?: emptyList())
        return AnimesPage(entries, hasNextPage)
    }

    private val titleRegex by lazy { Regex("[^A-Za-z0-9 ]")
    private fun titleToUrl(title: String) = titleRegex.replace(title, "")
        .trim().replace(Regex("\\s+"), "-").lowercase()

    private fun pageToSAnimes(page: List<Anime>): List<SAnime> = page.flatMap { anime ->
        anime.seasons.flatMapIndexed { seasonIndex, season ->
            val seasonTitle = anime.title + if (anime.seasons.size > 1) " S${seasonIndex + 1}" else ""
            val hasVostfr = season.episodes.any { it.languages.vo.players.isNotEmpty() }
            val hasVf = season.episodes.any { it.languages.vf.players.isNotEmpty() }
            listOfNotNull(
                if (hasVostfr) Triple("VOSTFR", "vo", hasVf) else null,
                if (hasVf) Triple("VF", "vf", hasVostfr) else null,
            ).map { (display, code, alt) ->
                SAnime.create().apply {
                    title = seasonTitle + if (alt) " ($display)" else ""
                    thumbnail_url = anime.poster
                    genre = anime.genres.joinToString()
                    status = parseStatus(anime.status, anime.seasons.size, seasonIndex + 1)
                    description = anime.description
                    setUrlWithoutDomain("/anime/${titleToUrl(anime.originalTitle)}?lang=$code&s=${seasonIndex + 1}")
                    initialized = true
                }
            }
        }
    }

    private fun parseStatus(statusString: String?, seasonCount: Int = 1, currentSeason: Int = 1): Int {
        if (currentSeason < seasonCount) return SAnime.COMPLETED
        return when (statusString?.trim()?.uppercase()) {
            "EN COURS" -> SAnime.ONGOING
            "TERMINÉ" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}