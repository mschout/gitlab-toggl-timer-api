package io.github.mschout.gitlab.toggltimer.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler

/**
 * Writes the `{ "authenticated": true, "redirectUrl": "/timer" }` payload that Spring Security 7's
 * WebAuthn JS expects. Mirrors `HttpMessageConverterAuthenticationSuccessHandler` but always routes
 * successful passkey logins to a fixed URL rather than honouring the request cache.
 */
class WebAuthnPrimaryLoginSuccessHandler(private val targetUrl: String) :
    AuthenticationSuccessHandler {

  private val converter = JacksonJsonHttpMessageConverter()

  override fun onAuthenticationSuccess(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authentication: Authentication,
  ) {
    val payload = mapOf("authenticated" to true, "redirectUrl" to (request.contextPath + targetUrl))
    converter.write(payload, MediaType.APPLICATION_JSON, ServletServerHttpResponse(response))
  }
}
