package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.common.utils.ContextHolder
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_asset_actions.view.assetActionsBuy
import kotlinx.android.synthetic.main.view_asset_actions.view.assetActionsReceive
import kotlinx.android.synthetic.main.view_asset_actions.view.assetActionsSend

class AssetActionsView @JvmOverloads constructor(
    override val providedContext: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(providedContext, attrs, defStyle), ContextHolder {

    init {
        orientation = HORIZONTAL

        View.inflate(context, R.layout.view_asset_actions, this)

        background = context.getCutCornerDrawable(R.color.blurColor)

        updatePadding(top = 4.dp, bottom = 4.dp)
    }

    val send: TextView
        get() = assetActionsSend

    val receive: TextView
        get() = assetActionsReceive

    val buy: TextView
        get() = assetActionsBuy
}