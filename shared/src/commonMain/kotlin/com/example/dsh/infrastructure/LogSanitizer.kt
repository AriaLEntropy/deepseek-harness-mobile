package com.example.dsh.infrastructure

internal object LogSanitizer {

    private val tokenRegex = Regex("token=[^&\\s]+")
    private val authRegex = Regex("Authorization:\\s*Bearer\\s+[^\\s]+", RegexOption.IGNORE_CASE)
    private val accessTicketRegex = Regex("access-ticket=[^&\\s]+")
    private val base64Regex = Regex("[A-Za-z0-9+/=]{65,}")
    private val passwordRegex = Regex("password=[^&\\s]+")
    private val apiKeyRegex = Regex("apiKey=[^&\\s]+", RegexOption.IGNORE_CASE)
    private val urlQuerySecretRegex = Regex("(&|\\?)(token|secret|key|password|signature|access_token)=[^&]+", RegexOption.IGNORE_CASE)
    private val jsonSecretValueRegex = Regex(
        """("[^"]*(?:token|api[_-]?key|access[_-]?token|authorization|clientToken|hostToken|secret|password)[^"]*"\s*:\s*)"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )

    fun sanitize(input: String): String {
        if (input.isEmpty()) return input
        return try {
            var result = input
            result = tokenRegex.replace(result) { "token=***" }
            result = authRegex.replace(result) { "Authorization: Bearer ***" }
            result = accessTicketRegex.replace(result) { "access-ticket=***" }
            result = base64Regex.replace(result) { "[base64:${it.value.length}chars]" }
            result = passwordRegex.replace(result) { "password=***" }
            result = apiKeyRegex.replace(result) { "apiKey=***" }
            result = urlQuerySecretRegex.replace(result) { match ->
                val eq = match.value.indexOf('=')
                match.value.substring(0, eq + 1) + "***"
            }
            result = jsonSecretValueRegex.replace(result) { match -> "${match.groupValues[1]}\"***\"" }
            result
        } catch (_: Exception) {
            "[redacted]"
        }
    }
}