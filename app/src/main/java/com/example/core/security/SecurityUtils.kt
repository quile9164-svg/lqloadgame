package com.example.core.security

object SecurityUtils {
    private val patternsToRedact = listOf(
        Regex("(?i)(access_token=)[^&\\s]+"),
        Regex("(?i)(sig=)[^&\\s]+"),
        Regex("(?i)(itopencodeparam=)[^&\\s]+"),
        Regex("(?i)(encodeparam=)[^&\\s]+"),
        Regex("(?i)(tmpSecretId=)[^&\\s]+"),
        Regex("(?i)(tmpSecretId\":\\s*\")[^\"]+"),
        Regex("(?i)(tmpSecretKey=)[^&\\s]+"),
        Regex("(?i)(tmpSecretKey\":\\s*\")[^\"]+"),
        Regex("(?i)(token=)[^&\\s]+"),
        Regex("(?i)(token\":\\s*\")[^\"]+"),
        Regex("(?i)(authorization:\\s*Bearer\\s+)[^\\s\\r\\n]+"),
        Regex("(?i)(authorization:\\s*)[^\\s\\r\\n]+"),
        Regex("(?i)(cookie:\\s*)[^\\r\\n]+")
    )

    fun redact(text: String?): String {
        if (text == null) return ""
        var redacted: String = text
        for (pattern in patternsToRedact) {
            redacted = pattern.replace(redacted) { matchResult ->
                val group1 = matchResult.groups[1]?.value ?: ""
                "$group1[REDACTED]"
            }
        }
        return redacted
    }
}
