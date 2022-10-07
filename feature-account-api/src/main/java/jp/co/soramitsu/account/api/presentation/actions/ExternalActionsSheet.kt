package jp.co.soramitsu.account.api.presentation.actions

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item

typealias ExternalViewCallback = (String) -> Unit
typealias CopyCallback = (String) -> Unit

open class ExternalActionsSheet(
    context: Context,
    private val payload: Payload,
    val onCopy: CopyCallback,
    val onViewExternal: ExternalViewCallback
) : FixedListBottomSheet(context) {

    class Payload(
        @StringRes val copyLabel: Int,
        val content: ExternalAccountActions.Payload
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(payload.content.value)

        val value = payload.content.value

        item(R.drawable.ic_copy_24, payload.copyLabel) {
            onCopy(value)
        }

        payload.content.explorers.map { (type, url) ->
            item(
                icon = R.drawable.ic_globe_24,
                title = context.resources.getString(R.string.view_in, type.capitalizedName),
                onClick = { onViewExternal(url) }
            )
        }
    }
}
