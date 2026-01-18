package br.com.soat.shared.model

abstract class ApplicationException(val error: Error): Throwable()