package mv.ahusan.einthusantv

import java.io.Serializable

data class Movie(
    val title: String,
    val url: String,
    val poster: String,
    val year: String,
    val synopsis: String,
    val subtitles: Boolean,
    // Used to find the same film in other languages
    val imdb: String = "",
    val director: String = "",
    val lead: String = "",
    val leadRole: String = ""
) : Serializable

/** The same film in another language. */
data class Version(
    val lang: String,
    val title: String,
    val url: String,
    val poster: String
)
