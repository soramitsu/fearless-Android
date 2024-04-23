package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
class CrossChainTransferDraft(
    val amount: BigDecimal,
    val originChainId: String,
    val destinationChainId: String,
    val originFee: BigDecimal,
    val destinationFee: BigDecimal,
    val chainAssetId: String,
    val recipientAddress: String,
    val tip: BigDecimal?,
    val transferableTokenSymbol: String
) : Parcelable
