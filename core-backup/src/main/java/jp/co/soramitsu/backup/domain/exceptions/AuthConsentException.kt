package jp.co.soramitsu.backup.domain.exceptions

import android.content.Intent

class AuthConsentException(
    val intent: Intent,
    cause: Throwable? = null
) : Exception(cause)
