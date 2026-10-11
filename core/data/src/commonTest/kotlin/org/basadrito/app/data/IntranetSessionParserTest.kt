package org.basadrito.app.data

import org.basadrito.app.data.intranet.parser.IntranetHtmlParser
import org.basadrito.app.data.intranet.parser.IntranetSessionParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntranetSessionParserTest {
    @Test
    fun profileWithoutReportedCodeIsRecognizedFromObservedMarkup() {
        val html = """<div class="m-card-profile__details">
            <span data-example="fixture" class='extra m-card-profile__name'>ESTUDIANTE&nbsp;
            DE PRUEBA</span><span class="m-card-profile__email">IDENTIFICADOR_FICTICIO</span>
            </div>"""
        assertTrue(IntranetSessionParser.isAuthenticatedProfile(html))
        val profile = IntranetHtmlParser.parseProfile(html)
        assertEquals("ESTUDIANTE DE PRUEBA", profile.fullName)
        assertEquals("", profile.code)
        assertEquals("", profile.email)
    }

    @Test
    fun loginErrorEmptyNameAndSimilarClassDoNotProveAccess() {
        for (html in listOf(
            "<h1>Nombre de usuario</h1>",
            """<span class="m-card-profile__name">&nbsp;</span>""",
            """<span class="m-card-profile__name-example">Estudiante</span>""",
            """<!-- <span class="m-card-profile__name">Estudiante</span> -->""",
            """<script>const template = '<span class="m-card-profile__name">Estudiante</span>';</script>""",
            """<input name="UserName"><input name="Password"><span class="m-card-profile__name">Estudiante</span>""",
        )) assertFalse(IntranetSessionParser.isAuthenticatedProfile(html))
    }
}
