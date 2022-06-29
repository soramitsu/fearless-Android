package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewAssetActionsBinding

class AssetActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewAssetActionsBinding

    init {
        orientation = HORIZONTAL

        inflate(context, R.layout.view_asset_actions, this)
        binding = ViewAssetActionsBinding.bind(this)

        background = context.getCutCornerDrawable(R.color.blurColor)

        updatePadding(top = 4.dp(context), bottom = 4.dp(context))
    }

    val send: TextView
        get() = binding.assetActionsSend

    val receive: TextView
        get() = binding.assetActionsReceive

    val buy: TextView
        get() = binding.assetActionsBuy
}
