package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
class TransferDraft(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val assetPayload: AssetPayload,
    val recipientAddress: String,
    val tip: BigDecimal?
) : Parcelable