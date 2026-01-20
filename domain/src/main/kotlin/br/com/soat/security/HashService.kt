package br.com.soat.security

import br.com.soat.config.Config
import java.security.SecureRandom
import org.bouncycastle.crypto.generators.OpenBSDBCrypt

class HashService(config: Config) {

    private val cost = config.getInt("security.hash.cost")

    fun hash(raw: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return OpenBSDBCrypt.generate(raw.toCharArray(), salt, cost)
    }

    fun check(raw: String, hash: String) =
        OpenBSDBCrypt.checkPassword(hash, raw.toCharArray())
}