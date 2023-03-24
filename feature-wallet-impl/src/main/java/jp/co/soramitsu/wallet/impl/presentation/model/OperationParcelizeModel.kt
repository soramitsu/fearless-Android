package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.parcelize.Parcelize
import java.math.BigInteger

sealed class OperationParcelizeModel : Parcelable {

    @Parcelize
    class Reward(
        val eventId: String,
        val address: String,
        val time: Long,
        val amount: String,
        val isReward: Boolean,
        val era: Int,
        val validator: String?
    ) : OperationParcelizeModel()

    @Parcelize
    class Extrinsic(
        val time: Long,
        val originAddress: String,
        val hash: String,
        val module: String,
        val call: String,
        val fee: String,
        val statusAppearance: OperationStatusAppearance
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
        val statusAppearance: OperationStatusAppearance
    ) : Parcelable, OperationParcelizeModel()

    @Parcelize
    class Swap(
        val id: String,
        val address: String,
        val hash: String,
        val time: Long,
        val module: String,
        val chainAsset: Asset,
        val targetAsset: Asset?,
        val baseAssetAmount: BigInteger,
        val liquidityProviderFee: BigInteger,
        val selectedMarket: String?,
        val targetAssetAmount: BigInteger?,
        val networkFee: BigInteger,
        val status: Operation.Status
    ) : Parcelable, OperationParcelizeModel()
}
