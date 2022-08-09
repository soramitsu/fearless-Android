package jp.co.soramitsu.featurewalletimpl.presentation.model

import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes

class OperationModel(
    val id: String,
    val time: Long,
    val amount: String,
    @ColorRes val amountColorRes: Int,
    val header: String,
    val statusAppearance: OperationStatusAppearance,
    val operationIcon: Drawable?,
    val subHeader: String,
    val assetIconUrl: String? = null
)
