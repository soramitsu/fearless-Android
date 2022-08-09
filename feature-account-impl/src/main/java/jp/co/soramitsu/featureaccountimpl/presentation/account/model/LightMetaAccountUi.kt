package jp.co.soramitsu.featureaccountimpl.presentation.account.model

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.utils.IgnoredOnEquals

data class LightMetaAccountUi(
    val id: Long,
    val name: String,
    val isSelected: Boolean,
    val picture: IgnoredOnEquals<Drawable>
)
