package org.basadrito.app.data.intranet.parser

internal fun hiddenInputValue(html: String, name: String): String? {
    for (input in Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
        val attributes = Regex("([\\w-]+)\\s*=\\s*(['\"])([\\s\\S]*?)\\2")
            .findAll(input.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
        if (attributes["name"] == name) return attributes["value"]?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")
    }
    return null
}
