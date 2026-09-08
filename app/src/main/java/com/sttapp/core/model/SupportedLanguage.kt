package com.sttapp.core.model

/** Top-5 languages by target region, per the MVP spec. */
enum class SupportedLanguage(
    val isoCode: String,
    val displayName: String,
) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    PORTUGUESE("pt", "Português"),
    SWAHILI("sw", "Kiswahili"),
    ARABIC("ar", "العربية"),
}
