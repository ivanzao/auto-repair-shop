package br.com.soat.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class JwtClaimsTest {

    private fun jwt(payload: String): String {
        val header = base64UrlEncode("""{"alg":"HS512","typ":"JWT"}""")
        val body = base64UrlEncode(payload)
        return "$header.$body.signature_not_validated"
    }

    private fun base64UrlEncode(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun `parses sub, role and cpf from JWT payload`() {
        val token = jwt(
            """{"sub":"d2d2c1e4-1111-2222-3333-aaaabbbbcccc","role":"ATTENDANT","cpf":"12345678909","exp":1234}"""
        )

        val claims = JwtClaims.parse(token)

        assertEquals("d2d2c1e4-1111-2222-3333-aaaabbbbcccc", claims!!.sub)
        assertEquals("ATTENDANT", claims.role)
        assertEquals("12345678909", claims.cpf)
    }

    @Test
    fun `returns null when token has wrong number of segments`() {
        assertNull(JwtClaims.parse("just.two"))
        assertNull(JwtClaims.parse("only-one"))
    }

    @Test
    fun `returns null when payload is not valid base64`() {
        assertNull(JwtClaims.parse("header.@@@not-base64@@@.sig"))
    }

    @Test
    fun `returns null when payload is missing sub, role or cpf`() {
        val noSub = jwt("""{"role":"ADMIN","cpf":"12345678909"}""")
        val noRole = jwt("""{"sub":"abc","cpf":"12345678909"}""")
        val noCpf = jwt("""{"sub":"abc","role":"ADMIN"}""")
        assertNull(JwtClaims.parse(noSub))
        assertNull(JwtClaims.parse(noRole))
        assertNull(JwtClaims.parse(noCpf))
    }
}
