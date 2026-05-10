package io.github.mschout.gitlab.toggltimer.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "user_auth_identities",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uq_user_auth_identity_provider_subject",
                columnNames = ["provider", "subject"],
            )
        ],
)
class UserAuthIdentity(
    @Column(nullable = false) var provider: String,
    @Column(nullable = false) var subject: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
)
