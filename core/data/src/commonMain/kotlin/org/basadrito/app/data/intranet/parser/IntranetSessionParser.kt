package org.basadrito.app.data.intranet.parser

object IntranetSessionParser {
    fun isAuthenticatedProfile(html: String): Boolean {
        val markup = visibleMarkup(html)
        return !isLoginForm(markup) && profileName(markup).isNotBlank()
    }

    fun isLoginForm(html: String): Boolean {
        val markup = visibleMarkup(html)
        val inputs = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(markup)
            .mapNotNull { attribute(it.value, "name") }.toSet()
        return inputs.any { it.equals("UserName", ignoreCase = true) } &&
            inputs.any { it.equals("Password", ignoreCase = true) }
    }
}

internal fun profileName(html: String): String {
    for (span in Regex("""<span\b([^>]*)>([\s\S]*?)</span\s*>""", RegexOption.IGNORE_CASE).findAll(visibleMarkup(html))) {
        val classes = attribute(span.groupValues[1], "class")?.split(Regex("""\s+""")) ?: continue
        if ("m-card-profile__name" in classes) {
            val name = span.groupValues[2].cleanHtml().replace(Regex("""\s+"""), " ").trim()
            if (name.isNotBlank()) return name
        }
    }
    return ""
}

private fun attribute(markup: String, name: String): String? =
    Regex("""(?:^|\s)$name\s*=\s*(['"])([\s\S]*?)\1""", RegexOption.IGNORE_CASE)
        .find(markup)?.groupValues?.get(2)

private fun visibleMarkup(html: String): String =
    Regex("""<!--[\s\S]*?-->|<script\b[^>]*>[\s\S]*?</script\s*>|<style\b[^>]*>[\s\S]*?</style\s*>""", RegexOption.IGNORE_CASE)
        .replace(html, "")
