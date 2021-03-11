package jp.co.soramitsu.common.address

import android.graphics.drawable.PictureDrawable

class AddressModel(
    val address: String,
    val image: PictureDrawable,
    val name: String? = null
) {
    val nameOrAddress = name ?: address
}