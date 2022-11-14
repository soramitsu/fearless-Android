package jp.co.soramitsu.wallet.impl.domain.model

import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.common.compose.theme.errorRed
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.coredb.model.PhishingLocal

data class PhishingModel(
    val address: String,
    val name: String?,
    val type: String?,
    val subtype: String?
) {
    val color: Color = when {
        type.equals("scam", true) -> errorRed
        else -> warningOrange
    }
}

fun PhishingLocal.toPhishingModel() = PhishingModel(address, name, type, subtype)
