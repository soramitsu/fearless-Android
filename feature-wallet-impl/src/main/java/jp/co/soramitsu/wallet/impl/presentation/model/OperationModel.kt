package jp.co.soramitsu.wallet.impl.presentation.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

class OperationModel(
    val id: String,
    val time: Long,
    val amount: String,
    val amountColor: Color = Color.White,
    val header: String,
    val statusAppearance: OperationStatusAppearance,
    val operationIcon: Drawable?,
    val subHeader: String,
    val assetIconUrl: String? = null
)
