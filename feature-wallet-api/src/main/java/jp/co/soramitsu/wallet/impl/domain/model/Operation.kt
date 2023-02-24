package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigInteger
import jp.co.soramitsu.common.utils.camelCaseToCapitalizedWords
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

data class Operation(
    val id: String,
    val address: String,
    val type: Type,
    val time: Long,
    val chainAsset: Chain.Asset
) {

    sealed class Type {
        fun formatted(value: String) = value.camelCaseToCapitalizedWords()

        data class Extrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: BigInteger,
            val status: Status
        ) : Type()

        data class Reward(
            val amount: BigInteger,
            val isReward: Boolean,
            val era: Int,
            val validator: String?
        ) : Type()

        data class Transfer(
            val hash: String?,
            val myAddress: String,
            val amount: BigInteger,
            val receiver: String,
            val sender: String,
            val status: Status,
            val fee: BigInteger?
        ) : Type()

        data class Swap(
            val hash: String,
            val module: String,
            val baseAssetAmount: BigInteger,
            val liquidityProviderFee: BigInteger,
            val selectedMarket: String?,
            val targetAsset: Chain.Asset?,
            val targetAssetAmount: BigInteger?,
            val networkFee: BigInteger,
            val status: Status
        ) : Type()
    }

    enum class Status {
        PENDING, COMPLETED, FAILED;

        companion object {
            fun fromSuccess(success: Boolean): Status {
                return if (success) COMPLETED else FAILED
            }
        }
    }
}
