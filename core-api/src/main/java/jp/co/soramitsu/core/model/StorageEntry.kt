package jp.co.soramitsu.core.model

import java.math.BigInteger

class StorageEntry(
    val storageKey: String,
    val content: String?,
    val runtimeVersion: BigInteger
)
