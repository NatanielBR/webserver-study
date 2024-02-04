package natanielbr.study.webserver.core

import kotlinx.coroutines.Runnable
import natanielbr.study.webserver.utils.MiddlewareList
import org.reflections.Reflections
import java.io.Closeable
import java.lang.reflect.Type
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class WebServer : Closeable {
    val controllerMap = mutableListOf<WebController>()
    val middlewares = MiddlewareList<Middleware>()

    private val serverSocket = ServerSocket(8080)
    val requestBodySerializerMap = RequestBodySerializerMap()
    var globalErrorHandler = object : HttpErrorHandlers {}
    var isInitialized = false

    init {
        // get all class using Annotation Path
        val reflections = Reflections("")
        reflections.getTypesAnnotatedWith(Path::class.java).forEach {
            val path = it.getAnnotation(Path::class.java)
            val controller = it.getDeclaredConstructor().newInstance() as WebController
            controllerMap.add(controller)

            it.getMethod("initialize", Path::class.java).invoke(controller, path)
        }
    }

    fun start() {
        if (isInitialized) {
            throw Exception("Server already started")
        }
        thread {
            isInitialized = true
            kotlin.runCatching {
                while (!serverSocket.isClosed) {
                    val socket = serverSocket.accept()

                    Thread(
                        SocketConnection(
                            socket,
                            globalErrorHandler,
                            controllerMap,
                            middlewares,
                            requestBodySerializerMap
                        )
                    ).start()
                }
            }.onFailure {
                if (it.message != "Socket closed") {
                    it.printStackTrace()
                }
            }
        }
    }


    companion object {
        fun serializeResponse(response: Any?): String {
            if (response == null) {
                return ""
            }

            return when (response) {
                is String -> {
                    response
                }

                else -> {
                    response.toString()
                }
            }
        }

        fun serializeParameter(parameter: Any, type: Type): Any {
            return when (type.typeName) {
                "java.lang.String" -> {
                    parameter as String
                }

                "java.lang.Integer", "int" -> {
                    parameter as Int
                }

                "java.util.List<java.lang.Integer>" -> {
                    (parameter as List<*>).map {
                        serializeParameter(it!!, Int::class.java)
                    }
                }

                else -> {
                    throw Exception("Type not supported")
                }
            }
        }
    }

    override fun close() {
        serverSocket.close()
    }

    private class SocketConnection(
        val socket: Socket,
        val globalErrorHandler: HttpErrorHandlers,
        val controllerMap: List<WebController>,
        val middlewareList: MiddlewareList<Middleware>,
        val requestBodySerializerMap: RequestBodySerializerMap,
    ) : Runnable {
        private val response = socket.getOutputStream().bufferedWriter()

        private fun respondHttp(_webResponse: WebResponse, exception: Throwable? = null) {
            var webResponse = _webResponse

            if (exception != null) {
                webResponse = globalErrorHandler.error500(exception, webResponse)
            } else if (webResponse.status == 404) {
                webResponse = globalErrorHandler.error404(webResponse)
            }

            response.write("HTTP/1.1 ${webResponse.status}\r\n")
            webResponse.headers.forEach { (key, value) ->
                response.write("$key: $value\r\n")
            }
            response.write("\r\n")
            response.write(webResponse.body)
            response.flush()
        }

        private fun getRequestData(socket: Socket): RequestData {
            val httpMethod: String
            val absolutePath: String
            val path: String // if GET, remove query string if not, keep it
            val requestHeaders = mutableMapOf<String, String>()
            var requestBody = ""

            socket.getInputStream().let {
                val builder = StringBuilder()
                Thread.sleep(20) // available() sometimes return 0
                var available: Int = it.available()

                while (available > 0) {
                    builder.append(it.read().toChar())
                    available = it.available()
                }

                builder.toString()
            }.also {
                val lines = it.split("\r\n")
                var lineIndex = 0

                val parts = lines[lineIndex++].split(" ")
                httpMethod = parts[0]
                absolutePath = if (parts[1].count { it == '/' } > 1) {
                    // example: /aa/bb/cc
                    parts[1].substring(1)
                } else {
                    // example: /aa
                    parts[1]
                }

                if (lines[lineIndex] == "") {
                    // sem headers
                    lineIndex++
                } else {
                    // le todos os headers
                    while (true) {
                        val headerParts = lines[lineIndex++].split(": ")
                        requestHeaders[headerParts[0].lowercase()] = headerParts[1]

                        if (lines[lineIndex] == "") {
                            break
                        }
                    }
                }

                if (lines[lineIndex] == "") {
                    // le o body
                    requestBody = lines[lineIndex + 1]
                }
            }

            if (httpMethod != "POST") {
                val parts = absolutePath.split("?", limit = 2)

                path = parts[0]
                if (parts.size != 1) {
                    requestBody = parts[1]
                }
            } else {
                path = absolutePath
            }

            val contentType = requestHeaders["content-type"]
            val bodySerialized: Map<String, Any?>? =
                if (contentType == null && requestBody.isNotEmpty() && httpMethod == "POST") {
                    // if there is a body, but no content-type, throw error
                    throw HttpException("Content-Type header is required", 400)
                } else {
                    if (contentType == null && requestBody.isEmpty() && httpMethod == "POST") {
                        // if there is no content-type, then the body is empty
                        throw HttpException("Content-Type header is required", 400)
                    } else if (httpMethod == "GET") {
                        requestBodySerializerMap.serialize("get_request", requestBody)
                    } else if (contentType != null) {
                        if (requestBodySerializerMap.hasSerializer(contentType)) {
                            requestBodySerializerMap.serialize(contentType, requestBody)!!
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

            if (bodySerialized == null) {
                throw HttpException("Invalid body", 400)
            }

            return RequestData(httpMethod, path, requestHeaders, bodySerialized, requestBody)
        }

        private fun findController(webRequest: WebRequest): WebController? {
            return controllerMap.firstOrNull { it.hasEndpoint(webRequest) }
        }

        override fun run() {
            socket.use {
                kotlin.runCatching {
                    var requestData = getRequestData(socket)

                    // process middlewares - before request
                    kotlin.runCatching {
                        middlewareList.getOrdered().forEach {
                            requestData = it.before(requestData)
                        }
                    }.onFailure {
                        if (it is HttpException) {
                            // throw more fancy error
                            respondHttp(
                                WebResponse(
                                    it.status,
                                    it.headers.toMutableMap(),
                                    it.message!!
                                ),
                                it
                            )
                            return@use
                        } else {
                            // throw unknown 500 error
                            respondHttp(
                                WebResponse(
                                    500,
                                    mutableMapOf(),
                                    ""
                                ),
                                it
                            )
                            return@use
                        }
                    }

                    // after middlewares, execute controller
                    // process path, to get controller and method
                    val paths = requestData.path.split("/", limit = 2)
                    var controllerPath = paths[0]
                    var controllerMethod = paths[1]

                    if (controllerPath == "") {
                        controllerPath = "/"
                    }
                    if (controllerMethod == "") {
                        controllerMethod = "index"
                    }
                    val webRequest = WebRequest(
                        controllerMethod, requestData.method,
                        requestData.headers, requestData.body, requestData.rawBody,
                        requestData.path
                    )

                    val controller = findController(webRequest)
                    var response = controller?.execute(
                        webRequest, requestBodySerializerMap
                    ) ?: kotlin.runCatching {
                        WebResponse(
                            404,
                            mutableMapOf(),
                            ""
                        )
                    }.getOrElse {
                        WebResponse(
                            500,
                            mutableMapOf(),
                            ""
                        )
                    }

                    // process middlewares - after request
                    kotlin.runCatching {
                        middlewareList.getOrdered().forEach {
                            response = it.after(response)
                        }
                    }.onFailure {
                        if (it is HttpException) {
                            // throw more fancy error
                            respondHttp(
                                WebResponse(
                                    it.status,
                                    it.headers.toMutableMap(),
                                    it.message!!
                                ),
                                it
                            )
                            return@use
                        } else {
                            // throw unknown 500 error
                            respondHttp(
                                WebResponse(
                                    500,
                                    mutableMapOf(),
                                    ""
                                ),
                                it
                            )
                            return@use
                        }
                    }

                    respondHttp(response)
                }.onFailure {
                    it.printStackTrace()
                    respondHttp(
                        WebResponse(
                            500,
                            mutableMapOf(),
                            ""
                        ),
                        it
                    )
                }
            }
        }

    }
}

annotation class Path(val path: String)

interface HttpErrorHandlers {
    fun error404(webResponse: WebResponse): WebResponse {
        webResponse.status = 404

        if (webResponse.body == "") {
            webResponse.body = "Error 404 - Not Found"
        }

        return webResponse
    }

    fun error500(exception: Throwable, webResponse: WebResponse): WebResponse {
        if (webResponse.status <= 0) {
            webResponse.status = 500
        }

        if (webResponse.body == "") {
            webResponse.body = "Error 500 - Internal Server Error"
        }

        return webResponse
    }
}

class HttpException(
    message: String,
    val status: Int = 500,
    val headers: Map<String, String> = mapOf(),
) : Exception(message)

interface Middleware {
    fun before(request: RequestData): RequestData
    fun after(response: WebResponse): WebResponse
}

open class MiddlewareAdapter : Middleware {
    override fun before(request: RequestData): RequestData {
        return request
    }

    override fun after(response: WebResponse): WebResponse {
        return response
    }
}

data class RequestData(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: Map<String, Any?>,
    val rawBody: String,
)