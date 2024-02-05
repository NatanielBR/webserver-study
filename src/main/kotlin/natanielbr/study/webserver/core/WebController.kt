package natanielbr.study.webserver.core

import natanielbr.study.webserver.core.WebServer.Companion.serializeParameter
import natanielbr.study.webserver.utils.StringUtils.count
import kotlin.reflect.KCallable
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.findParameterByName
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

open class WebController {
    private val methods = mutableMapOf<String, KCallable<*>>()
    private var path: Path? = null
    private lateinit var webRequest: WebRequest
    private lateinit var webResponse: WebResponse

    protected val request: WebRequest
        get() = webRequest
    protected val response: WebResponse
        get() = webResponse

    fun initialize(path: Path) {
        this.path = path
        val methods = this::class.declaredMembers

        methods.forEach {
            val postAnnotation = it.annotations.find { it is Post } as? Post
            val getAnnotation = it.annotations.find { it is Get } as Get?

            if (postAnnotation == null || postAnnotation.path.isEmpty()) {
                // no has postAnnotation or no path, use method name
                addMethod(it, "post", it.name)
            } else {
                // has postAnnotation and path, use path
                addMethod(it, "post", postAnnotation.path)
            }


            if (getAnnotation == null || getAnnotation.path.isEmpty() && postAnnotation == null) {
                // no has getAnnotation or no path, use method name and only if no postAnnotation
                addMethod(it, "get", it.name)
            } else {
                // has getAnnotation and path, use path
                val path = if (getAnnotation?.path?.isEmpty() == true) {
                    it.name
                } else {
                    getAnnotation.path
                }
                addMethod(it, "get", path)
            }
        }
    }

    private fun addMethod(method: KCallable<*>, httpMethod: String, _path: String) {
        val path = _path.removePrefix("/")
        methods["${httpMethod}_$path"] = method
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getMethodFromWebRequest(webRequest: WebRequest): KCallable<*>? {
        methods.forEach { (path, route) ->
            val urlParameter = getUrlParameters(path)

            val pathParts = path.split("_")
            val method = pathParts[0]
            val routePath = pathParts[1]

            if (urlParameter.isEmpty()) {
                // normal url
                val isValid = if (routePath.count("/") > 0) {
                    // multiple path, use absolute path
                    // absolute path always starts with /
                    method == webRequest.method.lowercase() && routePath == webRequest.absolutePath.removePrefix("/")
                } else {
                    // single path, use path
                    method == webRequest.method.lowercase() && routePath == webRequest.path
                }

                if (isValid) {
                    return route
                }
            } else {
                // url with parameters
                val regexBuilder = StringBuilder()

                routePath.split("/").forEach {
                    if (it.startsWith(":")) {
                        regexBuilder.append("/[a-zA-Z0-9.]+")
                    } else {
                        regexBuilder.append("/$it")
                    }
                }

                val regex = regexBuilder.toString().removePrefix("/")

                if (
                    method == webRequest.method.lowercase()
                    && regex.toRegex().matches(webRequest.absolutePath)
                ) {
                    val parts = webRequest.absolutePath.split("/")
                    urlParameter.forEachIndexed { index, param ->
                        webRequest.urlParameters[param] = parts[index + 1]
                    }

                    // instance is a parameter
                    if (route.parameters.size > 1) {
                        // method has parameters
                        // check if parameters is same type

                        runCatching {
                            webRequest.urlParameters.forEach {
                                val parameter = route.findParameterByName(it.key)

                                // elvis operator is not beautiful
                                @Suppress("FoldInitializerAndIfToElvis", "RedundantSuppression")
                                if (parameter == null) {
                                    // parameter not found
                                    return@runCatching
                                }

                                serializeParameter(it.value, parameter.type.javaType)
                            }
                            return route
                        }
                    }

                    // validations not passed
                    // deleting url parameters
                    webRequest.urlParameters.clear()
                }
            }
        }

        return null
    }

    private fun getUrlParameters(path: String): List<String> {
        val urlParams = mutableListOf<String>()

        val pathParts = path.split("/")

        pathParts.forEachIndexed { index, part ->
            if (part.startsWith(":")) {
                urlParams.add(part.removePrefix(":"))
            }
        }

        return urlParams
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun runEndpoint(
        method: KCallable<*>,
        request: WebRequest,
        requestBodySerializer: BodySerializerMap
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

                } else if (it.type.jvmErasure.java.packageName == "java.lang") {
                    // primitive parameter
                    var paramValue = request.body[it.name]
                    if (paramValue == null && webRequest.urlParameters.isNotEmpty()) {
                        paramValue = webRequest.urlParameters[it.name]
                    }

                    if (paramValue == null) throw Exception("Parameter ${it.name} not found")
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
    fun execute(webRequest: WebRequest, bodySerializer: BodySerializerMap): WebResponse? {
        this.webRequest = webRequest
        webResponse = WebResponse(
            200, mutableMapOf(
                "content-type" to "text/html",
            ), ""
        )

        val method = getMethodFromWebRequest(webRequest) ?: return null

        val bodyAny = runEndpoint(method, request, bodySerializer)

        this.webResponse.body = bodySerializer.serializeResponse(
            response.contentType,
            bodyAny
        )

        return this.webResponse
    }

    fun hasEndpoint(webRequest: WebRequest): Boolean {
        return getMethodFromWebRequest(webRequest) != null
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
    var urlParameters = mutableMapOf<String, String>()
    val contentType: String?
        get() = headers["content-type"]
}

class WebResponse(
    var status: Int,
    val headers: MutableMap<String, String>,
    var body: String
) {
    var contentType: String
        get() = headers["content-type"] ?: "text/html"
        set(value) {
            headers["content-type"] = value
        }

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
