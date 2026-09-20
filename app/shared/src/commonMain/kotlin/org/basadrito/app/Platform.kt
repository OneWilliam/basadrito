package org.basadrito.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform