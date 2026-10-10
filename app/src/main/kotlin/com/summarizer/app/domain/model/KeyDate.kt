package com.summarizer.app.domain.model

/**
 * An event or deadline named in a summary's DATES section, e.g. "17.10 7:30 | Wycieczka, autobus".
 *
 * @property label What happens ("Wycieczka")
 * @property date ISO date (yyyy-MM-dd); null when the model gave none or it could not be resolved ("w środę")
 * @property time Time of day as HH:mm, if given
 * @property detail Extra information such as the place, or the unresolved date text
 */
@kotlinx.serialization.Serializable
data class KeyDate(
    val label: String,
    val date: String? = null,
    val time: String? = null,
    val detail: String? = null
)
