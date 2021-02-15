package jp.co.soramitsu.common.account

import android.graphics.drawable.PictureDrawable

class AddressModel(
    val address: String,
    val image: PictureDrawable,
    val name: String? = null
)