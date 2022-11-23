package jp.co.soramitsu.wallet.impl.domain.model

import androidx.compose.ui.graphics.Color
import java.util.Locale
import jp.co.soramitsu.common.compose.theme.errorRed
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.coredb.model.PhishingLocal

enum class PhishingType {
    SCAM, EXCHANGE, DONATION, SANCTIONS, UNKNOWN;

    companion object {
        fun from(value: String) = when (value.lowercase()) {
            "scam" -> SCAM
            "exchange" -> EXCHANGE
            "donation" -> DONATION
            "sanctions" -> SANCTIONS
            else -> UNKNOWN
        }
    }

    fun title(): String {
        return name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
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
