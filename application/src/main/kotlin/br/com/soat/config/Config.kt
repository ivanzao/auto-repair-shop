package br.com.soat.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.collections.iterator

class Config private constructor(
    private val flat: Map<String, Any?>
) {

    fun getString(key: String, defaultValue: String? = null): String =
        get(key, defaultValue) { it.toString() }

    fun getInt(key: String, defaultValue: Int? = null): Int =
        get(key, defaultValue) {
            when (it) {
                is Int -> it
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                    ?: throw IllegalArgumentException("Key '$key' is not a valid integer: '$it'")

                else -> throw IllegalArgumentException("Key '$key' not found and default value was not provided")
            }
        }

    fun getBoolean(key: String, defaultValue: Boolean? = null): Boolean =
        get(key, defaultValue) {
            when (it) {
                is Boolean -> it
                is String -> it.equals("true", ignoreCase = true)
                else -> throw IllegalArgumentException("Key '$key' not found and default value was not provided")
            }
        }

    fun size() = flat.size

    private fun <T> get(key: String, defaultValue: T?, transformerFunc: (Any) -> T): T =
        flat[key]?.let { transformerFunc(it) } ?: defaultValue
        ?: throw IllegalArgumentException("Key '$key' not found and default value was not provided")

    companion object {
        private val mapper = YAMLMapper().registerKotlinModule()

        fun fromClasspath(path: String): Config {
            val stream = Config::class.java.classLoader.getResourceAsStream(path)
                ?: throw IllegalArgumentException("File not found within classpath: $path")

            return buildConfig(mapper.readValue(stream))
        }

        private fun buildConfig(raw: Map<String, Any?>): Config {
            val flat = flatten(raw)

            val merged: Map<String, Any?> = flat.mapValues { (k, v) ->
                searchAsEnvironmentVariable(k) ?: v
            }

            return Config(merged)
        }

        private fun flatten(
            source: Map<String, Any?>,
            parentKey: String = "",
            separator: String = "."
        ): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()

            for ((key, value) in source) {
                val newKey = if (parentKey.isEmpty()) key else "$parentKey$separator$key"

                when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        result.putAll(flatten(value as Map<String, Any?>, newKey, separator))
                    }

                    is List<*> -> {
                        value.forEachIndexed { index, item ->
                            val listKey = "$newKey[$index]"

                            if (item is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                result.putAll(flatten(item as Map<String, Any?>, listKey, separator))
                            } else {
                                result[listKey] = item
                            }
                        }
                    }

                    else -> result[newKey] = value
                }
            }

            return result
        }

        private fun searchAsEnvironmentVariable(key: String): Any? {
            val envVarName = key.uppercase().replace(".", "_")
            return System.getenv()[envVarName]
        }
    }
}
