package jp.co.soramitsu.wallet.impl.presentation.send

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import kotlinx.parcelize.Parcelize

@Parcelize
open class TransferDraft(
    open val amount: BigDecimal,
    open val fee: BigDecimal,
    open val assetPayload: AssetPayload,
    open val recipientAddress: String,
    open val tip: BigDecimal?
) : Parcelable

@Parcelize
class CBDCTransferDraft(
    override val amount: BigDecimal,
    override val fee: BigDecimal,
    override val assetPayload: AssetPayload,
    override val recipientAddress: String,
    override val tip: BigDecimal?,
    val cbdcAddressId: String
): TransferDraft(amount, fee, assetPayload, recipientAddress, tip)
