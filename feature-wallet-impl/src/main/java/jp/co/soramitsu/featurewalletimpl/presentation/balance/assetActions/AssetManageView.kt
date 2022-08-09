package jp.co.soramitsu.featurewalletimpl.presentation.balance.assetActions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewAssetsManageBinding

class AssetManageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewAssetsManageBinding

    init {
        inflate(context, R.layout.view_assets_manage, this)
        binding = ViewAssetsManageBinding.bind(this)

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }
    }

    val warning: View
        get() = binding.assetsManageWarning

    fun setActionClickListener(listener: (View) -> Unit) {
        binding.assetsManageAction.setOnClickListener(listener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionClickListener(listener)
    }
}
