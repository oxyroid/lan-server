package org.oxyroid.lanserver

import io.ktor.server.routing.Route


fun interface Endpoint {
    fun apply(route: Route)
}