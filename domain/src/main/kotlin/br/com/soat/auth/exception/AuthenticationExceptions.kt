package br.com.soat.auth.exception

import br.com.soat.shared.exception.ApplicationException

class InvalidLoginCredentialsException : ApplicationException("AUT-001", "Invalid credentials")
class InvalidRefreshTokenException : ApplicationException("AUT-002", "Invalid refresh token")