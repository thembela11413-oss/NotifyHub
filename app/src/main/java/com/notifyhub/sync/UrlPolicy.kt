package com.notifyhub.sync

sealed interface UrlCheck {
    data class Valid(val url: String) : UrlCheck
    data class Invalid(val reason: String) : UrlCheck
}

/** HTTPS by default; HTTP only when the user explicitly enabled local-development mode. */
object UrlPolicy {
    private val pattern = Regex("^https?://[^\\s/?#]+([/?#]\\S*)?$", RegexOption.IGNORE_CASE)

    fun validate(input: String, allowHttp: Boolean): UrlCheck {
        val url = input.trim().trimEnd('/')
        if (url.isEmpty()) return UrlCheck.Invalid("Enter a server URL.")
        if (!pattern.matches(url)) return UrlCheck.Invalid("Enter a full URL such as https://notify.example.com.")
        if (url.startsWith("http://", ignoreCase = true) && !allowHttp) {
            return UrlCheck.Invalid("HTTP is disabled. Use https:// or enable HTTP for local development.")
        }
        return UrlCheck.Valid(url)
    }
}
