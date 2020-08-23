package jp.co.soramitsu.common.view

import android.app.Dialog
import android.content.Context
import jp.co.soramitsu.common.R

class FearlessProgressDialog(
    context: Context
) : Dialog(context) {

    init {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCancelable(false)
        setContentView(R.layout.layout_loading)
    }
}