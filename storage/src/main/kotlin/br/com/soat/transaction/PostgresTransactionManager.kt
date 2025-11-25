package br.com.soat.transaction

import br.com.soat.shared.repository.RepositoryTransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class PostgresTransactionManager : RepositoryTransactionManager {

    override fun <T> inTransaction(function: () -> T): T = transaction { function() }

}