package org.oxyroid.lanserver

interface HttpServer {
    fun start(port: Int)
    fun stop()
}