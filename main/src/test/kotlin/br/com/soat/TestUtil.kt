package br.com.soat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail

fun waitFor(timeout: Long = 10000, func: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeout) {
        if (func()) return
    }

    fail("Timeout waiting for function to return true")
}

fun assertIsUUID(uuid: String, message: String) {
    val regex = Regex("[0-9a-fA-F-]{8}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{12}")
    assertTrue(regex.matches(uuid), message)
}