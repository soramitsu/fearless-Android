@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package jp.co.soramitsu.feature_account_api.domain.model

import java.math.BigDecimal

abstract class Palette(val index: UByte) {
    abstract val calls: List<Call>

    operator fun contains(callIndex: Pair<UByte, UByte>): Boolean {
        return calls.any { it.index == callIndex }
    }

    inner class Call(_index: UByte) {
        val index = this@Palette.index to _index
    }
}

class PredefinedPalettes(
    val transfers: Transfers
) {

    class Transfers(index: UByte) : Palette(index) {
        val transfer = Call(0U)

        val transferKeepAlive = Call(3U)

        override val calls = listOf(transfer, transferKeepAlive)
    }
}

class RuntimeConfiguration(
    val pallets: PredefinedPalettes,
    val genesisHash: String,
    val addressByte: Byte,
    val existentialDeposit: BigDecimal
)