package ai.pipecat.client.support

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Whether two JSON values are the same, as they are on the wire: numbers compare by value, so
 * `5` is `5.0`.
 */
fun same(a: JsonElement, b: JsonElement): Boolean = when {
    a is JsonObject && b is JsonObject ->
        a.keys == b.keys && a.all { (key, value) -> same(value, b.getValue(key)) }

    a is JsonArray && b is JsonArray ->
        a.size == b.size && a.indices.all { same(a[it], b[it]) }

    a is JsonPrimitive && b is JsonPrimitive && a.isNumber && b.isNumber ->
        a.doubleOrNull == b.doubleOrNull

    else -> a == b
}

/**
 * Whether this value has all of [expected]: every field of an object, with what it has, and
 * every item of an array.
 */
fun JsonElement.carries(expected: JsonElement): Boolean = when {
    this is JsonObject && expected is JsonObject ->
        expected.all { (key, value) -> this[key]?.carries(value) ?: false }

    this is JsonArray && expected is JsonArray ->
        size == expected.size && indices.all { this[it].carries(expected[it]) }

    else -> same(this, expected)
}

private val JsonPrimitive.isNumber
    get() = this !is JsonNull && !isString && booleanOrNull == null

/**
 * Validates JSON against the document's schemas, with the keywords they use.
 */
class SchemaValidator(private val schemas: JsonObject) {

    /**
     * What is wrong with a value, by a schema, each with where it is.
     */
    fun violations(value: JsonElement, schema: JsonElement, path: String = ""): List<String> {
        val rules = schema as? JsonObject ?: return emptyList()
        val found = mutableListOf<String>()

        rules["\$ref"]?.jsonPrimitive?.content?.let { ref ->
            val target = schemas[ref.substringAfterLast("/")]
                ?: return listOf("$path refers to the unknown schema $ref")
            found += violations(value, target, path)
        }
        (rules["allOf"] as? JsonArray)?.forEach { found += violations(value, it, path) }
        (rules["anyOf"] as? JsonArray)?.let { options ->
            if (options.none { violations(value, it, path).isEmpty() }) {
                found += "$path matches none of its schema's options"
            }
        }
        rules["not"]?.let { excluded ->
            if (violations(value, excluded, path).isEmpty()) {
                found += "$path matches a schema it must not"
            }
        }
        rules["const"]?.let { constant ->
            if (!same(value, constant)) {
                found += "$path is $value, not $constant"
            }
        }
        (rules["enum"] as? JsonArray)?.let { values ->
            if (values.none { same(value, it) }) {
                found += "$path is $value, not one of $values"
            }
        }
        rules["type"]?.jsonPrimitive?.content?.let { type ->
            if (!matches(value, type)) {
                found += "$path is $value, not of type $type"
            }
        }

        if (value is JsonObject) {
            val properties = rules["properties"] as? JsonObject ?: JsonObject(emptyMap())
            (rules["required"] as? JsonArray)?.forEach {
                val key = it.jsonPrimitive.content
                if (key !in value) {
                    found += "$path/$key is required"
                }
            }
            val additional = rules["additionalProperties"]
            for ((key, item) in value) {
                val property = properties[key]
                when {
                    property != null -> found += violations(item, property, "$path/$key")
                    additional is JsonPrimitive && additional.booleanOrNull == false ->
                        found += "$path/$key isn't allowed"

                    additional is JsonObject -> found += violations(item, additional, "$path/$key")
                }
            }
        }
        if (value is JsonArray) {
            rules["minItems"]?.jsonPrimitive?.doubleOrNull?.let { minimum ->
                if (value.size < minimum) {
                    found += "$path has fewer than ${minimum.toInt()} items"
                }
            }
            rules["items"]?.let { itemSchema ->
                value.forEachIndexed { index, item ->
                    found += violations(item, itemSchema, "$path/$index")
                }
            }
        }
        return found
    }

    private fun matches(value: JsonElement, type: String): Boolean = when (type) {
        "null" -> value is JsonNull
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        "number" -> value is JsonPrimitive && value.isNumber
        "integer" -> value is JsonPrimitive && value.isNumber &&
            value.doubleOrNull.let { it != null && it == Math.rint(it) }

        "string" -> value is JsonPrimitive && value.isString
        "array" -> value is JsonArray
        "object" -> value is JsonObject
        else -> false
    }
}
