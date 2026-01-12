package br.com.soat

import org.junit.jupiter.api.fail

fun waitFor(timeout: Long = 10000, func: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeout) {
        if (func()) return
        Thread.sleep(100)
    }

    fail("Timeout waiting for function to return true")
}