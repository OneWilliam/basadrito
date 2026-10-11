package org.basadrito.app.data.intranet.parser

import org.basadrito.app.data.intranet.dto.StudentProfile


object IntranetHtmlParser {

    fun parseProfile(html: String): StudentProfile {
        return StudentProfile(fullName = profileName(html))
    }
}
