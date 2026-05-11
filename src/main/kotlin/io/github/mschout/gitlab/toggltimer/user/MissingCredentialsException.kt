package io.github.mschout.gitlab.toggltimer.user

class MissingCredentialsException(val credentialKind: String) :
    RuntimeException("Authenticated user has no $credentialKind credential configured")
