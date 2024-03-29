package natanielbr.study.webserver.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class BodySerializerMap {
    private val map: MutableMap<String, BodySerializer> = mutableMapOf()

    init {
        addSerializer("application/json", JsonBodySerializer())
        addSerializer("get_request", GetBodySerializer())
        addSerializer("text/html", GetBodySerializer()) // html is a simple text
    }

    fun addSerializer(contentType: String, serializer: BodySerializer) {
        map[contentType] = serializer
    }

    fun hasSerializer(contentType: String): Boolean {
        return map.containsKey(contentType)
    }

    /**
     * Will serialize the request body to a Map<String, Any>.
     *
     * @param contentType Content type of the request, or "get_request" if it's a GET request
     */
    fun serialize(contentType: String, obj: String): Map<String, Any?>? {
        return map[contentType]?.serialize(obj)
    }

    fun<R> serializeObject(contentType: String, rawBody: String, jclass: Class<R>): R? {
        return map[contentType]?.serialize(rawBody, jclass)
    }

    fun serializeResponse(contentType: String, obj: Any?): String {
        return map[contentType]?.serializeResponse(obj) ?: "" // prevent "null" string
    }
}

interface BodySerializer {
    /**
     * Irá serializar o corpo da requisição para um Map<String, Any>, que será um padrão
     * interno para o framework.
     * Caso seja um array, a key deve ser "_" e o value deve ser uma lista.
     *
     * @param obj Request body
     * @return Translated request body, or null if it's not possible to serialize
     */
    fun serialize(obj: String): Map<String, Any?>?

    fun serializeResponse(obj: Any?): String
    fun<R> serialize(obj: String, jclass: Class<R>): R
}

class JsonBodySerializer : BodySerializer {
    private val objectMapper = jacksonObjectMapper()
    override fun serialize(obj: String): Map<String, Any?>? {
        return kotlin.runCatching {
            val json = objectMapper.readTree(obj)

            if (json.isObject) {
                json.fields().asSequence().toList().associate { it.key to it.value.asText() }
            } else {
                mapOf("_" to json.toList().map { serializeField(it) })
            }
        }.getOrNull()
    }

    override fun <R> serialize(obj: String, jclass: Class<R>): R {
        return objectMapper.readValue(obj, jclass)
    }

    override fun serializeResponse(obj: Any?): String {
        return objectMapper.writeValueAsString(obj)
    }

    private fun serializeField(node: JsonNode): Any? {
        return if (node.isLong) {
            node.asLong()
        } else if (node.isInt) {
            node.asInt()
        } else if (node.isDouble) {
            node.asDouble()
        } else if (node.isBoolean) {
            node.asBoolean()
        } else if (node.isNull) {
            null
        } else {
            node.asText()
        }
    }
}

class GetBodySerializer : BodySerializer {
    override fun serialize(obj: String): Map<String, Any> {
        return if (obj.isEmpty()) {
            mapOf()
        } else {
            obj.split("&").map { it.split("=") }.associate { it[0] to it[1] }
        }
    }

    override fun <R> serialize(obj: String, jclass: Class<R>): R {
        val params = serialize(obj)
        val instance = jclass.getDeclaredConstructor(
            *params.values.map { it::class.java }.toTypedArray()
        ).newInstance(*params.values.toTypedArray())

        return instance
    }

    override fun serializeResponse(obj: Any?): String {
        // prevent throw exception
        return obj.toString()
    }
}