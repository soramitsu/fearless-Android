package jp.co.soramitsu.wallet.impl.domain.model

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
data class QrContentCBDC(
    val transactionAmount: BigDecimal,
    val transactionCurrencyCode: String,
    val description: String?,
    val name: String,
    val billNumber: String?,
    val recipientId: String
) : Parcelable
