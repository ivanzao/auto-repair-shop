package br.com.soat.shared.util

fun Any.readFileFromResource(path: String): String =
    this.javaClass.classLoader.getResource(path)?.readText()
        ?: throw RuntimeException("File not found $path")