package jp.co.soramitsu.feature_wallet_impl.presentation.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

sealed class OperationParcelizeModel {
    @Parcelize
    class RewardModel(
        val hash: String,
        val address: String,
        val accountName: String?,
        val time: Long,
        val amount: BigDecimal,
        val formattedAmount: String,
        val isReward: Boolean,
        val era: Int,
        val validator: String,
        val isFailed: Boolean,
        val isIncome: Boolean,
        @DrawableRes val iconId: Int,
        @StringRes val messageId: Int,
    ) : Parcelable, OperationParcelizeModel()

    @Parcelize
    class ExtrinsicModel(
        val time: Long,
        val address: String,
        val accountName: String?,
        val displayAddress: String,
        val hash: String,
        val module: String,
        val call: String,
        val fee: BigDecimal,
        val formattedFee: String,
        val success: Boolean,
        val operationHeader: String,
        val elementDescription: String?,
        @DrawableRes val iconId: Int,
        @StringRes val messageId: Int,
    ) : Parcelable, OperationParcelizeModel()

    @Parcelize
    class TransferModel(
        val time: Long,
        val address: String,
        val accountName: String?,
        val hash: String,
        val amount: BigDecimal,
        val receiver: String,
        val sender: String,
        val fee: BigDecimal,
        val isIncome: Boolean,
        val displayAddress: String,
        val formattedAmount: String,
        val formattedFee: String,
        val tokenType: Token.Type,
        val isFailed: Boolean,
        @DrawableRes val iconId: Int,
        @StringRes val messageId: Int,
    ) : Parcelable, OperationParcelizeModel()
}
