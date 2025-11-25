package br.com.soat.shared.repository

interface RepositoryTransactionManager {
    fun <T> inTransaction(function: () -> T): T
}