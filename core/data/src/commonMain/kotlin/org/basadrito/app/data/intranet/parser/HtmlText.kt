package org.basadrito.app.data.intranet.parser


/** Usual html entities */
private val HTML_ENTITIES: Map<String, String> = buildMap {
    put("nbsp", " "); put("amp", "&"); put("lt", "<"); put("gt", ">")
    put("quot", "\""); put("apos", "'"); put("emsp", " "); put("ensp", " ")
    put("iexcl", "\u00A1"); put("cent", "\u00A2"); put("pound", "\u00A3"); put("curren", "\u00A4")
    put("yen", "\u00A5"); put("brvbar", "\u00A6"); put("sect", "\u00A7"); put("uml", "\u00A8")
    put("copy", "\u00A9"); put("ordf", "\u00AA"); put("laquo", "\u00AB"); put("not", "\u00AC")
    put("shy", "\u00AD"); put("reg", "\u00AE"); put("macr", "\u00AF"); put("deg", "\u00B0")
    put("plusmn", "\u00B1"); put("sup2", "\u00B2"); put("sup3", "\u00B3"); put("acute", "\u00B4")
    put("micro", "\u00B5"); put("para", "\u00B6"); put("middot", "\u00B7"); put("cedil", "\u00B8")
    put("sup1", "\u00B9"); put("ordm", "\u00BA"); put("raquo", "\u00BB"); put("frac14", "\u00BC")
    put("frac12", "\u00BD"); put("frac34", "\u00BE"); put("iquest", "\u00BF")
    put("Agrave", "\u00C0"); put("Aacute", "\u00C1"); put("Acirc", "\u00C2"); put("Atilde", "\u00C3")
    put("Auml", "\u00C4"); put("Aring", "\u00C5"); put("AElig", "\u00C6"); put("Ccedil", "\u00C7")
    put("Egrave", "\u00C8"); put("Eacute", "\u00C9"); put("Ecirc", "\u00CA"); put("Euml", "\u00CB")
    put("Igrave", "\u00CC"); put("Iacute", "\u00CD"); put("Icirc", "\u00CE"); put("Iuml", "\u00CF")
    put("ETH", "\u00D0"); put("Ntilde", "\u00D1"); put("Ograve", "\u00D2"); put("Oacute", "\u00D3")
    put("Ocirc", "\u00D4"); put("Otilde", "\u00D5"); put("Ouml", "\u00D6"); put("times", "\u00D7")
    put("Oslash", "\u00D8"); put("Ugrave", "\u00D9"); put("Uacute", "\u00DA"); put("Ucirc", "\u00DB")
    put("Uuml", "\u00DC"); put("Yacute", "\u00DD"); put("THORN", "\u00DE"); put("szlig", "\u00DF")
    put("agrave", "\u00E0"); put("aacute", "\u00E1"); put("acirc", "\u00E2"); put("atilde", "\u00E3")
    put("auml", "\u00E4"); put("aring", "\u00E5"); put("aelig", "\u00E6"); put("ccedil", "\u00E7")
    put("egrave", "\u00E8"); put("eacute", "\u00E9"); put("ecirc", "\u00EA"); put("euml", "\u00EB")
    put("igrave", "\u00EC"); put("iacute", "\u00ED"); put("icirc", "\u00EE"); put("iuml", "\u00EF")
    put("eth", "\u00F0"); put("ntilde", "\u00F1"); put("ograve", "\u00F2"); put("oacute", "\u00F3")
    put("ocirc", "\u00F4"); put("otilde", "\u00F5"); put("ouml", "\u00F6"); put("divide", "\u00F7")
    put("oslash", "\u00F8"); put("ugrave", "\u00F9"); put("uacute", "\u00FA"); put("ucirc", "\u00FB")
    put("uuml", "\u00FC"); put("yacute", "\u00FD"); put("thorn", "\u00FE"); put("yuml", "\u00FF")
}

private val TAG_REGEX = Regex("<[^>]+>")
private val DECIMAL_ENTITY = Regex("&#(\\d+);")
private val HEX_ENTITY = Regex("&#[xX]([0-9a-fA-F]+);")
private val NAMED_ENTITY = Regex("&([a-zA-Z]+);")

fun decodeHtmlEntities(text: String): String {
    var result = DECIMAL_ENTITY.replace(text) { m ->
        m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
    }
    result = HEX_ENTITY.replace(result) { m ->
        m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
    }
    result = NAMED_ENTITY.replace(result) { m ->
        HTML_ENTITIES[m.groupValues[1]] ?: m.value
    }
    return result
}

fun String.cleanHtml(): String = decodeHtmlEntities(TAG_REGEX.replace(this, "")).trim()

fun extractCells(fragment: String): List<String> {
    val cellRegex = Regex("<t[dh][^>]*>([\\s\\S]*?)</t[dh]>")
    return cellRegex.findAll(fragment).map { it.groupValues[1].cleanHtml() }.toList()
}

fun extractNumber(text: String): String =
    Regex("""\d+\.?\d*""").find(text)?.value ?: text
