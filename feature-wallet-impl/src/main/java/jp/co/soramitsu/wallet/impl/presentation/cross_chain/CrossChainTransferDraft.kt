package jp.co.soramitsu.wallet.impl.presentation.cross_chain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class CrossChainTransferDraft(
    val amount: BigDecimal,
    val originalChainId: String,
    val destinationChainId: String,
    val originalFee: BigDecimal,
    val destinationFee: BigDecimal,
    val chainAssetId: String,
    val recipientAddress: String,
    val tip: BigDecimal?,
    val transferableTokenSymbol: String
) : Parcelable
