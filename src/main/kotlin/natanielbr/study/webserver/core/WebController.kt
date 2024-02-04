package natanielbr.study.webserver.core

import natanielbr.study.webserver.core.WebServer.Companion.serializeParameter
import natanielbr.study.webserver.core.WebServer.Companion.serializeResponse
import kotlin.reflect.KCallable
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.javaType
import kotlin.reflect.jvm.jvmErasure

interface WebParameter {
    fun isText(): Boolean
    fun asText(): String
    fun isInt(): Boolean
    fun asInt(): String
    fun isLong(): Boolean
    fun asLong(): String
    fun isDouble(): Boolean
    fun asDouble(): String
    fun isBoolean(): Boolean
    fun asBoolean(): String
    fun isNull(): Boolean
//    fun isArray(): Boolean
}

interface WebGetParameter : WebParameter {
    fun isArray(): Boolean
    fun asAsArray(): List<*>
}

open class WebController : HttpErrorHandlers {
    private val methods = mutableMapOf<String, KCallable<*>>()
    private lateinit var webRequest: WebRequest
    private lateinit var webResponse: WebResponse

    protected val request: WebRequest
        get() = webRequest
    protected val response: WebResponse
        get() = webResponse

    fun initialize() {
        val methods = this::class.declaredMembers

        methods.forEach {
            val postAnnotation = it.annotations.find { it is Post } as? Post
            var path: String
            val method = if (postAnnotation != null) {
                path = if (postAnnotation.path.isEmpty()) {
                    // use method name
                    it.name
                } else {
                    postAnnotation.path.removePrefix("/")
                }
                "post"
            } else {
                path = it.name
                "get"
            }

            this.methods["${method}_$path"] = it

            // has Get annotation
            val getAnnotation = it.annotations.find { it is Get } as Get?
            if (getAnnotation != null) {
                path = if (getAnnotation.path.isEmpty()) {
                    // use method name
                    it.name
                } else {
                    getAnnotation.path.removePrefix("/")
                }
                this.methods["get_$path"] = it
            }
        }
    }

    private fun getMethodFromWebRequest(webRequest: WebRequest): KCallable<*>? {
        val urlParameter = getUrlParameters(webRequest.path)

        methods.forEach { (path, route) ->
            val pathParts = path.split("_")
            val method = pathParts[0]
            val routePath = pathParts[1]

            if (urlParameter.isEmpty()) {
                // normal url
                if (method == webRequest.method.lowercase() && routePath == webRequest.path) {
                    return route
                }
            } else {
                // url with parameters
                // todo: implementar
//                val regexBuilder = StringBuilder()
//
//                routePath.split("/").forEach {
//                    if (it.startsWith(":")) {
//                        regexBuilder.append("/[a-zA-Z0-9]+")
//                    } else {
//                        regexBuilder.append("/$it")
//                    }
//                }
//
//                val regex = regexBuilder.toString().removePrefix("/")
//
//                if (method == webRequest.method.lowercase() && regex.toRegex().matches(webRequest.path)) {
//                    return route
//                }
            }
        }

        return null
    }

    private fun getUrlParameters(path: String): Map<String, String> {
        val urlParams = mutableMapOf<String, String>()

        val pathParts = path.split("/")

        val thisPathParts = webRequest.path.split("/")

        pathParts.forEachIndexed { index, part ->
            if (part.startsWith(":")) {
                urlParams[part.removePrefix(":")] = thisPathParts[index]
            }
        }

        return urlParams
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun runEndpoint(
        method: KCallable<*>,
        request: WebRequest,
        requestBodySerializer: RequestBodySerializerMap
    ): Any? {
        val methodParams = mutableListOf<Any>()

        method.parameters.forEach {
            if (it.type.jvmErasure.isInstance(this)) {
                // first parameter is instance
                methodParams.add(this)
            } else {

                if (
                    (it.type.jvmErasure.java == List::class.java || it.type.jvmErasure.java == Array::class.java)
                    && request.body.containsKey("_")
                ) {
                    // parametro é um array e a raiz do json é um array
                    val serialized = serializeParameter(request.body["_"]!!, it.type.javaType)
                    methodParams.add(serialized)

                } else if (it.type.jvmErasure.java.`package`.name == "java.lang") {
                    // primitive parameter
                    val paramValue = request.body[it.name] ?: throw Exception("Parameter ${it.name} not found")
                    val serialized = serializeParameter(paramValue, it.type.javaType)

                    methodParams.add(serialized)
                } else {
                    // any parameter
                    val contentType = webRequest.contentType
                    if (contentType != null) {
                        val paramValue = requestBodySerializer.serializeObject(
                            contentType, request.rawBody, it.type.jvmErasure.java
                        )

                        if (paramValue != null) {
                            methodParams.add(paramValue)
                        }
                    }


                }

            }
        }

        return method.call(*methodParams.toTypedArray())
    }

    /**
     * Executa o método com o nome [path] e com os parâmetros [parameters]
     * @param path Nome do método a ser executado, sem a barra inicial e de forma relativa
     * @param parameters Parâmetros a serem passados para o método, com & separando os parâmetros
     */
    fun execute(webRequest: WebRequest, requestBodySerializer: RequestBodySerializerMap): WebResponse {
        this.webRequest = webRequest
        webResponse = WebResponse(
            200, mutableMapOf(
                "content-type" to "text/html",
            ), ""
        )

        val method = getMethodFromWebRequest(webRequest)

        if (method == null) {
            webResponse = error404(webResponse)
            return webResponse
        }

        this.webResponse.body = serializeResponse(runEndpoint(method, request, requestBodySerializer))

        return this.webResponse
    }

}

data class WebRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val body: Map<String, Any?>,
    val rawBody: String,
    val absolutePath: String
) {
    val contentType: String?
        get() = headers["content-type"]
}

class WebResponse(
    var status: Int,
    val headers: MutableMap<String, String>,
    var body: String
) {
    fun serialize(): String {
        val builder = StringBuilder()

        builder.append("HTTP/1.1 $status\r\n")

        headers.forEach { (key, value) ->
            builder.append("$key: $value\r\n")
        }

        builder.append("\r\n")
        builder.append(body)

        return builder.toString()
    }
}

annotation class Post(
    val contentType: String = "application/json",
    val path: String = "",
)
annotation class Get(
    val path: String = "",
)
