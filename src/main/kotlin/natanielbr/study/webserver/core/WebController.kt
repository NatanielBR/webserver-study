package natanielbr.study.webserver.core

import com.fasterxml.jackson.databind.ObjectMapper
import natanielbr.study.webserver.core.WebServer.Companion.serializeParameter
import natanielbr.study.webserver.core.WebServer.Companion.serializeResponse
import kotlin.math.log
import kotlin.reflect.KCallable
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.javaType
import kotlin.reflect.jvm.jvmErasure

open interface WebParameter {
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

open interface WebGetParameter : WebParameter {
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

    private fun init() {
        val methods = this::class.declaredMembers

        methods.forEach {
            val postAnnotation = it.annotations.find { it is Post } as? Post
            var method = if (postAnnotation != null) {
                "post"
            } else {
                "get"
            }

            this.methods["${method}_${it.name}"] = it

            // has Get annotation
            if (it.annotations.find { it is Get } != null) {
                this.methods["get_${it.name}"] = it
            }
        }
    }

    private fun parallelizeParam(node: WebParameter, methodParam: KType): Any? {
        // check if T is String
        if (node.isText()) {
            return node.asText()
        }

        // check if T is Int
        if (node.isInt()) {
            return node.asInt()
        } else if (node.isLong()) {
            return node.asLong()
        }

        // check if T is Float
        if (node.isDouble()) {
            return node.asDouble()
        }

        // check if T is Boolean
        if (node.isBoolean()) {
            return node.asBoolean()
        }

//        if (node.isObject) {
//            val obj = Utils.GLOBAL_OBJ_MAPPER.treeToValue(node, methodParam.jvmErasure.java)
//            return obj!!
//        }

//        if (node.isArray) {
//            val list = mutableListOf<Any?>()
//            node.forEach {
//                val arrType = methodParam.arguments[0]
//                list.add(parallelizeParam(it, arrType.type!!))
//            }
//            return list
//        }

        if (node.isNull()) {
            return null
        }

        throw NotImplementedError()
    }

    /**
     * Executa o método com o nome [path] e com os parâmetros [parameters]
     * @param path Nome do método a ser executado, sem a barra inicial e de forma relativa
     * @param parameters Parâmetros a serem passados para o método, com & separando os parâmetros
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun execute(webRequest: WebRequest, parameters: String): WebResponse {
        if (methods.isEmpty()) {
            init()
        }

        this.webRequest = webRequest
        this.webResponse = WebResponse(
            200, mutableMapOf(
                "content-type" to "text/html",
            ), ""
        )

        val method = methods["${request.method.lowercase()}_${webRequest.path}"]

        if (method == null) {
            this.webResponse.status = 404
            this.webResponse.body = serializeResponse(error404())
            return this.webResponse
        }

        kotlin.runCatching {
            val methodParams = mutableListOf<Any>()
            val httpParameters: Map<String, String> = if (webRequest.method == "GET") {
                if (parameters.isEmpty()) {
                    mapOf()
                } else {
                    parameters.split("&").map { it.split("=") }.associate { it[0] to it[1] }
                }
            } else {
                if (webRequest.headers["content-type"] == "application/json") {
                    kotlin.runCatching {
                        val objectMapper = ObjectMapper()

                        val obj = objectMapper.readTree(parameters)
                        // checa se é um obj, se for continua, se não por enquanto retorna um erro
                        // mas depois terá que fazer o serialize do array para o método
                        if (obj.isObject) {
                            // checa se todos os valores não são objetos ou arrays
                            obj.fields().forEach {
                                if (it.value.isObject || it.value.isArray) {
                                    throw Exception("Invalid JSON")
                                }
                            }
                            obj.fields().asSequence().toList().associate { it.key to it.value.asText() }
                        } else if (obj.isArray) {
                            // caso o json raiz seja um array, retorna um map
                            // com a chave $array e o valor sendo o array
                            mapOf("\$array" to obj.asText())
                        } else {
                            mapOf()
                        }
                    }.getOrElse {
                        // todo: depois melhorar isso, esta feio
                        this.webResponse.status = 400
                        this.webResponse.body = serializeResponse(error500(it))

                        return@execute this.webResponse
                    }
                } else {
                    mapOf()
                }
            }

            method.parameters.forEach {
                if (it.type.jvmErasure.isInstance(this)) {
                    // first parameter is instance
                    methodParams.add(this)
                } else {

                    if (
                        (it.type.jvmErasure.java == List::class.java || it.type.jvmErasure.java == Array::class.java)
                        && httpParameters.containsKey("\$array")
                    ) {
                        // parametro é um array e a raiz do json é um array
                        val serialized = serializeParameter(httpParameters["\$array"]!!, it.type.javaType)
                        methodParams.add(serialized)

                    } else {
                        // any parameter
                        val paramValue = httpParameters[it.name] ?: throw Exception("Parameter ${it.name} not found")
                        val serialized = serializeParameter(paramValue, it.type.javaType)

                        methodParams.add(serialized)
                    }

                }
            }

            this.webResponse.body = serializeResponse(method.call(*methodParams.toTypedArray()))
        }.onFailure {
            this.webResponse.status = 500
            this.webResponse.body = serializeResponse(error500(it))
        }

        return this.webResponse
    }

    override fun error404(): Any {
        return "Error 404 - Not Found"
    }

    override fun error500(exception: Throwable): Any {
        exception.printStackTrace()
        return "Error 500 - Internal Server Error"
    }

    override fun anyError(status: Int): Any {
        return "Error $status"
    }
}

data class WebRequest(
    val path: String,
    val method: String,
    val headers: MutableMap<String, String>,
    val body: String,
    val absolutePath: String
)

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

annotation class Post(val contentType: String = "application/json")
annotation class Get()
