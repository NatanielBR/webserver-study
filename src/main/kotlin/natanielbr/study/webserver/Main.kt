package natanielbr.study.webserver

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import natanielbr.study.webserver.core.WebServer

fun main() {
    runBlocking {
        val server = WebServer()

        server.start()
    }
}