package jp.co.soramitsu.feature_wallet_impl.presentation.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

sealed class OperationParcelizeModel : Parcelable {

    @Parcelize
    class Reward(
        val eventId: String,
        val address: String,
        val time: Long,
        val amount: String,
        val isReward: Boolean,
        val era: Int,
        val validator: String?,
    ) : OperationParcelizeModel()

    @Parcelize
    class Extrinsic(
        val time: Long,
        val originAddress: String,
        val hash: String,
        val module: String,
        val call: String,
        val fee: String,
        val statusAppearance: OperationStatusAppearance,
    ) : Parcelable, OperationParcelizeModel()

    @Parcelize
    class Transfer(
        val time: Long,
        val address: String,
        val hash: String?,
        val isIncome: Boolean,
        val amount: String,
        val total: String,
        val receiver: String,
        val sender: String,
        val fee: String,
        val statusAppearance: OperationStatusAppearance,
    ) : Parcelable, OperationParcelizeModel()
}
