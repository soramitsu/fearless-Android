package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_assets_manage.view.assetsManageAction
import kotlinx.android.synthetic.main.view_assets_manage.view.assetsManageWarning

class AssetManageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    init {
        View.inflate(context, R.layout.view_assets_manage, this)

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }
    }

    val warning: View
        get() = assetsManageWarning

    fun setActionClickListener(listener: (View) -> Unit) {
        assetsManageAction.setOnClickListener(listener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionClickListener(listener)
    }
}
