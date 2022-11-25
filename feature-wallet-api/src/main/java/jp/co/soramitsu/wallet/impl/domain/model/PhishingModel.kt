package jp.co.soramitsu.wallet.impl.domain.model

import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.common.compose.theme.errorRed
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.coredb.model.PhishingLocal

enum class PhishingType {
    SCAM, EXCHANGE, DONATION, SANCTIONS, UNKNOWN;

    val capitalizedName: String = name.lowercase().replaceFirstChar { it.titlecase() }

    companion object {
        fun from(value: String) = when (value.lowercase()) {
            "scam" -> SCAM
            "exchange" -> EXCHANGE
            "donation" -> DONATION
            "sanctions" -> SANCTIONS
            else -> UNKNOWN
        }
    }
}

data class PhishingModel(
    val address: String,
    val name: String?,
    val type: PhishingType,
    val subtype: String?
) {
    val color: Color = when (type) {
        PhishingType.SCAM -> errorRed
        else -> warningOrange
    }
}

fun PhishingLocal.toPhishingModel() = PhishingModel(address, name, PhishingType.from(type), subtype)
