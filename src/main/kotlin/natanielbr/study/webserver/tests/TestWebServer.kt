package natanielbr.study.webserver.tests

import natanielbr.study.webserver.core.WebServer
import java.net.http.HttpClient
import kotlin.random.Random

object TestWebServer {

    fun useTestServer(server: WebServer = WebServer(Random.nextInt(9000, 10000)), func: SimpleHttpClient.(WebServer) -> Unit) {
        val simpleHttpClient = SimpleHttpClient("http://localhost:${server.port}")


        kotlin.runCatching {
            server.start()
        }.onFailure {
            if (it.message != "Server already started") {
                throw it
            }
        }

        func(simpleHttpClient, server)
    }

}

data class SimpleResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, List<String>>
)

class SimpleHttpClient(
    private val baseUrl: String
) {
    private val httpClient = HttpClient.newHttpClient()

    fun get(path: String, headers: Map<String, String> = mapOf()): SimpleResponse {
        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("$baseUrl$path"))
            .also {
                headers.forEach { (k, v) -> it.header(k, v) }
            }
            .GET()
            .build()

        val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())

        return SimpleResponse(
            response.statusCode(),
            response.body(),
            response.headers().map()
        )
    }

    fun post(path: String, body: String, headers: Map<String, String> = mapOf()): SimpleResponse {
        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("$baseUrl$path"))
            .also {
                headers.forEach { (k, v) -> it.header(k, v) }
            }
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())

        return SimpleResponse(
            response.statusCode(),
            response.body(),
            response.headers().map()
        )
    }
}