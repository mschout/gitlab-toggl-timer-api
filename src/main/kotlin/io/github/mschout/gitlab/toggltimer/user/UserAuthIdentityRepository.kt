package io.github.mschout.gitlab.toggltimer.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserAuthIdentityRepository : JpaRepository<UserAuthIdentity, Long> {
  fun findByProviderAndSubject(provider: String, subject: String): UserAuthIdentity?
}
