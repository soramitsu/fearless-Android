package jp.co.soramitsu.feature_wallet_api.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal

data class Operation(
    val hash: String,
    val address: String,
    val accountName: String?,
    val type: Type,
    val time: Long,
    val tokenType: Token.Type,
    val nextPageCursor: String? = null
) {

    sealed class Type(
        val header: String?,
        val subheader: String?,
        val displayAmount: BigDecimal, // amount that we show on UI (might be fee)
        val status: Status
    ) {
        class Extrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: BigDecimal,
            val success: Boolean
        ) : Type(call, module, fee, Status.fromSuccess(success))

        class Reward(
            val amount: BigDecimal,
            val isReward: Boolean,
            val era: Int,
            val validator: String
        ) : Type(if (isReward) "Reward" else "Slash", "Staking", amount, Status.FAILED)

        class Transfer(
            val amount: BigDecimal,
            val receiver: String,
            val sender: String,
            val fee: BigDecimal
        ) : Type(null, "Transfer", amount, Status.COMPLETED)

        // Real amount without fee
        fun getOperationAmount() = when (this) {
            is Reward -> amount
            is Transfer -> amount
            is Extrinsic -> null
        }

        fun getOperationFee() = when (this) {
            is Reward -> null
            is Transfer -> fee
            is Extrinsic -> fee
        }
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
