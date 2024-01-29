package natanielbr.study.webserver.core

import natanielbr.study.webserver.example.HelloController
import org.reflections.Reflections
import java.net.ServerSocket

class WebServer {
    val controllerMap = mutableMapOf<String, WebController>()

    var globalErrorHandler = DefaultHttpErrorHandlers()

    init {
        // get all class using Annotation Path
        val reflections = Reflections("")
        reflections.getTypesAnnotatedWith(Path::class.java).forEach {
            val path = it.getAnnotation(Path::class.java).path
            val controller = it.getDeclaredConstructor().newInstance() as WebController
            controllerMap[path] = controller
        }
    }

    fun start() {
        ServerSocket(8080).use { server ->
            while (true) {
                val socket = server.accept()
                val method: String
                var path: String
                socket.getInputStream().bufferedReader().readLine().also {
                    val parts = it.split(" ")
                    method = parts[0]
                    path = if (parts[1].count { it == '/' } > 1) {
                        // example: /aa/bb/cc
                        parts[1].substring(1)
                    } else {
                        // example: /aa
                        parts[1]
                    }
                }
                println("""
                    method: $method
                    path: $path
                """.trimIndent())

                val response = socket.getOutputStream().bufferedWriter()

                val paths = path.split("/", limit = 2)
                var controllerPath = paths[0]
                var controllerMethod = paths[1]
                var parameters = ""

                run {
                    val parts = controllerMethod.split("?", limit = 2)

                    if (parts.size == 1) {
                        controllerMethod = parts[0]
                    } else {
                        controllerMethod = parts[0]
                        parameters = parts[1]
                    }
                }

                if (controllerPath == "") {
                    controllerPath = "/"
                }
                if (controllerMethod == "") {
                    controllerMethod = "index"
                }

                println("""
                    controllerPath: $controllerPath
                    controllerMethod: $controllerMethod
                    parameters: $parameters
                """.trimIndent())

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
                    val result = controller.execute(WebRequest(controllerMethod, method, path), parameters)

                    response.write(result.serialize())
                }

                response.flush()
                socket.close()
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

        fun serializeParameter(parameter: String, type: Class<*>): Any {
            return when (type) {
                String::class.java -> {
                    parameter
                }

                Int::class.java -> {
                    parameter.toInt()
                }

                else -> {
                    throw Exception("Type not supported")
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