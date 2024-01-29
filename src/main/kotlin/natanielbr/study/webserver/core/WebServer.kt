package natanielbr.study.webserver.core

import natanielbr.study.webserver.example.HelloController
import org.reflections.Reflections
import java.net.ServerSocket

class WebServer {
    val controllerMap = mutableMapOf<String, WebController>()

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
                    path = parts[1]
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
                    response.write("HTTP/1.1 404 Not Found\r\n")
                    response.write("Content-Type: text/html\r\n")
                } else {
                    response.write("HTTP/1.1 200 OK\r\n")
                    response.write("Content-Type: text/html\r\n")
                }
                val result = controller?.execute(controllerMethod, "")

                response.write("\r\n")
                response.write(serializeResponse(result))
                response.write("\r\n")
                response.flush()
                socket.close()
            }
        }
    }

    private fun serializeResponse(response: Any?): String {
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

annotation class Path(val path: String)