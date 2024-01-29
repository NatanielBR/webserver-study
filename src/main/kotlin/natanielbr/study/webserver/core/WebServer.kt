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
                    path = parts[1].substring(1)
                }
                val response = socket.getOutputStream().bufferedWriter()

                val paths = path.split("/", limit = 2)
                var controllerPath = paths[0]
                var controllerMethod = paths[1]

                if (controllerPath == "") {
                    controllerPath = "/"
                }
                if (controllerMethod == "") {
                    controllerMethod = "index"
                }

                val controller = controllerMap[controllerPath]
                if (controller == null) {
                    val result = kotlin.runCatching {

                        response.write("HTTP/1.1 404\r\n")
                        response.write("Content-Type: text/html\r\n")

                        globalErrorHandler.error404()
                    }.onFailure {
                        response.write("HTTP/1.1 500\r\n")
                        response.write("Content-Type: text/html\r\n")
                        globalErrorHandler.error500()
                    }

                    response.write("\r\n")
                    response.write(result.getOrNull()!!.toString())
                } else {
                    val result = controller.execute(WebRequest(controllerMethod, method, path), "")

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
    }
}

annotation class Path(val path: String)

interface HttpErrorHandlers {
    fun error404(): Any
    fun error500(): Any

    fun anyError(status: Int): Any
}

class DefaultHttpErrorHandlers : HttpErrorHandlers {
    override fun error404(): Any {
        return "Error 404 - Not Found"
    }

    override fun error500(): Any {
        return "Error 500 - Internal Server Error"
    }

    override fun anyError(status: Int): Any {
        return "Error $status"
    }
}