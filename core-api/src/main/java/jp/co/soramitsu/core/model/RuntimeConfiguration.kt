@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package jp.co.soramitsu.core.model

class RuntimeConfiguration(
    val genesisHash: String,
    val erasPerDay: Int,
    val addressByte: Byte,
)
