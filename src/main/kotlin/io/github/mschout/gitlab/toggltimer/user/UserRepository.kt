package io.github.mschout.gitlab.toggltimer.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
  fun findByEmail(email: String): User?
}
