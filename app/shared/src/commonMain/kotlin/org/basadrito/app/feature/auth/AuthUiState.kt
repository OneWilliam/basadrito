package org.basadrito.app.feature.auth

import org.basadrito.app.domain.auth.PortalAccess

data class AuthUiState(
    val username: String = "",
    val moodlePassword: String = "",
    val intranetPassword: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val errorMessage: String? = null,
    val access: PortalAccess? = null,
)
