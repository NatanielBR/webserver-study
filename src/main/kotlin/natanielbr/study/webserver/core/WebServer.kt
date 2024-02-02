package natanielbr.study.webserver.core

import kotlinx.coroutines.*
import kotlinx.coroutines.Runnable
import org.reflections.Reflections
import java.io.Closeable
import java.lang.reflect.Type
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread
import kotlin.math.log
import kotlin.reflect.full.declaredMembers

class WebServer : Closeable {
    val controllerMap = mutableMapOf<String, WebController>()
    private val serverSocket = ServerSocket(8080)
    var globalErrorHandler = DefaultHttpErrorHandlers()
    var isInitialized = false

    init {
        // get all class using Annotation Path
        val reflections = Reflections("")
        reflections.getTypesAnnotatedWith(Path::class.java).forEach {
            val path = it.getAnnotation(Path::class.java).path
            val controller = it.getDeclaredConstructor().newInstance() as WebController
            controllerMap[path] = controller

            it.getMethod("initialize").invoke(controller)
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
                    println("Pegou: ${socket.hashCode()}")

                    Thread(SocketConnection(socket, globalErrorHandler, controllerMap)).start()
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

        fun serializeParameter(parameter: String, type: Type): Any {
            return when (type.typeName) {
                "java.lang.String" -> {
                    parameter
                }

                "java.lang.Integer" -> {
                    parameter.toInt()
                }

                "java.util.List<java.lang.Integer>" -> {
                    parameter.split(",").map {
                        serializeParameter(it, Int::class.java)
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
        val controllerMap: Map<String, WebController>
    ) : Runnable {
        override fun run() {
            socket.use {
                kotlin.runCatching {
                    val method: String
                    var path: String
                    val requestHeaders = mutableMapOf<String, String>()
                    var requestBody = ""

                    socket.getInputStream()
                        .let {
                            val builder = StringBuilder()
                            Thread.sleep(20) // available() sometimes return 0
                            var available: Int = it.available()

                            while (available > 0) {
                                builder.append(it.read().toChar())
                                available = it.available()
                            }

                            builder.toString()
                        }
                        .also {
                            val lines = it.split("\r\n")
                            var lineIndex = 0

                            val parts = lines[lineIndex++].split(" ")
                            method = parts[0]
                            path = if (parts[1].count { it == '/' } > 1) {
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

                    println(
                        """
                    method: $method
                    path: $path
                """.trimIndent()
                    )

                    val response = socket.getOutputStream().bufferedWriter()

                    val paths = path.split("/", limit = 2)
                    var controllerPath = paths[0]
                    var controllerMethod = paths[1]
                    run {
                        if (method != "POST") {
                            val parts = controllerMethod.split("?", limit = 2)

                            if (parts.size == 1) {
                                controllerMethod = parts[0]
                            } else {
                                controllerMethod = parts[0]
                                requestBody = parts[1]
                            }
                        }
                    }

                    if (controllerPath == "") {
                        controllerPath = "/"
                    }
                    if (controllerMethod == "") {
                        controllerMethod = "index"
                    }

                    println(
                        """
                    controllerPath: $controllerPath
                    controllerMethod: $controllerMethod
                    parameters: $requestBody
                """.trimIndent()
                    )

                    val controller = controllerMap[controllerPath]
                    if (controller == null) {
                        val result = kotlin.runCatching {

                            response.write("HTTP/1.1 404\r\n")
                            response.write("Content-Type: text/html\r\n")

                            globalErrorHandler.error404()
                        }.onFailure {
                            response.write("HTTP/1.1 500\r\n")
                            response.write("Content-Type: text/html\r\n")
                            globalErrorHandler.error500(it)
                        }

                        response.write("\r\n")
                        response.write(result.getOrNull()!!.toString())
                    } else {
                        val result = controller.execute(
                            WebRequest(
                                controllerMethod, method,
                                requestHeaders, requestBody,
                                path
                            ), requestBody
                        )

                        response.write(result.serialize())
                    }

                    response.flush()
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }

    }
}

annotation class Path(val path: String)

interface HttpErrorHandlers {
    fun error404(): Any
    fun error500(exception: Throwable): Any

    fun anyError(status: Int): Any
}

class DefaultHttpErrorHandlers : HttpErrorHandlers {
    override fun error404(): Any {
        return "Error 404 - Not Found"
    }

    override fun error500(exception: Throwable): Any {
        return "Error 500 - Internal Server Error"
    }

    override fun anyError(status: Int): Any {
        return "Error $status"
    }
}