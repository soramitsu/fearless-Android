package jp.co.soramitsu.wallet.impl.presentation.cross_chain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

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
