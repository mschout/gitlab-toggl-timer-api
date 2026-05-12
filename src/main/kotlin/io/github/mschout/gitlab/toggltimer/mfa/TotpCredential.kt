package io.github.mschout.gitlab.toggltimer.mfa

import io.github.mschout.gitlab.toggltimer.security.EncryptedStringConverter
import io.github.mschout.gitlab.toggltimer.user.User
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "totp_credentials")
class TotpCredential(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(nullable = false) var label: String,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false)
    var secret: String,
    @Column(nullable = false) var confirmed: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "last_used_at") var lastUsedAt: Instant? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
)
