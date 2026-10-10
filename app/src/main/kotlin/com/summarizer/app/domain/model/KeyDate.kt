package com.summarizer.app.domain.model

/**
 * An event or deadline named in a summary's DATES section, e.g. "17.10 7:30 | Wycieczka, autobus".
 *
 * Stored as JSON in the summary row, so new fields need a default (older summaries lack them).
 *
 * @property label What happens ("Wycieczka")
 * @property date ISO date (yyyy-MM-dd); null when the model gave none or it could not be resolved ("w środę")
 * @property time When it starts, as HH:mm, if given ("19:30" for "19:30–21:00")
 * @property detail Extra information such as the place, or the unresolved date text
 * @property endTime When it ends, as HH:mm, for a time range ("21:00" for "19:30–21:00")
 */
@kotlinx.serialization.Serializable
data class KeyDate(
    val label: String,
    val date: String? = null,
    val time: String? = null,
    val detail: String? = null,
    val endTime: String? = null
)
