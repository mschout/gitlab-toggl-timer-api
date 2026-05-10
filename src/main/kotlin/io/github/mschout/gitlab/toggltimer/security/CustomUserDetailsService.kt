package io.github.mschout.gitlab.toggltimer.security

import io.github.mschout.gitlab.toggltimer.user.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {
  override fun loadUserByUsername(username: String): UserDetails {
    val user =
        userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("No user for $username")
    val hash =
        user.passwordHash
            ?: throw UsernameNotFoundException("User $username has no local password set")
    return SpringUser.builder()
        .username(user.email)
        .password(hash)
        .disabled(!user.enabled)
        .authorities(user.roles.map { SimpleGrantedAuthority(it) })
        .build()
  }
}
