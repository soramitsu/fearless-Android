package jp.co.soramitsu.wallet.impl.presentation.send

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import kotlinx.parcelize.Parcelize

@Parcelize
class TransferDraft(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val assetPayload: AssetPayload,
    val recipientAddress: String,
    val tip: BigDecimal?,
    val message: String?
) : Parcelable