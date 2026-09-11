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
        """("[^"]*(?:token|api[_-]?key|access[_-]?ticket|authorization|secret|password)[^"]*"\s*:\s*)"(?:\\.|[^"\\])*"""",
        RegexOption.IGNORE_CASE,
    )
    private val namedSecret = Regex("""\b((?:api[_-]?key|access[_-]?ticket|clientToken|hostToken|token|secret|password)\s*[=:]\s*)(?:"(?:\\.|[^"\\])*"|'[^']*'|[^&\s,}]+)""", RegexOption.IGNORE_CASE)
    private val bearer = Regex("""\bBearer\s+[^\s"',}]+""", RegexOption.IGNORE_CASE)
    private val dataUri = Regex("""data:[^\s"']*;base64,[A-Za-z0-9+/=_-]*""", RegexOption.IGNORE_CASE)
    private val attachmentData = Regex("""("(?:dataBase64|dataUrl|bytesBase64|base64)"\s*:\s*)"(?:\\.|[^"\\])*"""", RegexOption.IGNORE_CASE)
    private val plainApiKey = Regex("""\bsk-[A-Za-z0-9_-]{8,}""")
    private val basicAuth = Regex("""(Authorization\s*[:=]\s*)Basic\s+[^\s"',}]+""", RegexOption.IGNORE_CASE)
    private val imagePayload = Regex("""("(?:text|delta|arguments|argumentsDelta|data)"\s*:\s*)"(?:\\.|[^"\\])*"""", RegexOption.IGNORE_CASE)

    fun sanitize(event: LogEvent): LogEvent = event.copy(
        type = sanitize(event.type), sessionId = event.sessionId?.let(::sanitize),
        rpcId = event.rpcId?.let(::sanitize), message = sanitize(event.message),
    )

    fun sanitize(input: String): String {
        if (input.isEmpty()) return input
        return try {
            var result = input
            result = plainApiKey.replace(result, "***")
            result = basicAuth.replace(result) { "${it.groupValues[1]}***" }
            result = imagePayload.replace(result) { "${it.groupValues[1]}\"[payload omitted]\"" }
            result = dataUri.replace(result, "[attachment omitted]")
            result = attachmentData.replace(result) { "${it.groupValues[1]}\"***\"" }
            result = jsonSecretValueRegex.replace(result) { "${it.groupValues[1]}\"***\"" }
            result = bearer.replace(result, "Bearer ***")
            result = namedSecret.replace(result) { "${it.groupValues[1]}***" }
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
            result
        } catch (_: Exception) {
            "[redacted]"
        }
    }
}
