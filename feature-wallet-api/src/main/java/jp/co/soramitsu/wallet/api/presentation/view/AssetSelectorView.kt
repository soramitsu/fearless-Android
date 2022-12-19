package jp.co.soramitsu.wallet.api.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.utils.getEnum
import jp.co.soramitsu.common.utils.useAttributes
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.databinding.ViewAssetSelectorBinding
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel

class AssetSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    enum class BackgroundStyle {
        BLURRED, BORDERED
    }

    private val binding: ViewAssetSelectorBinding

    init {
        inflate(context, R.layout.view_asset_selector, this)
        binding = ViewAssetSelectorBinding.bind(this)

        attrs?.let {
            applyAttributes(it)
        }
    }

    private fun applyAttributes(attributes: AttributeSet) = context.useAttributes(attributes, R.styleable.AssetSelectorView) {
        val backgroundStyle: BackgroundStyle = it.getEnum(R.styleable.AssetSelectorView_backgroundStyle, BackgroundStyle.BORDERED)

        setBackgroundStyle(backgroundStyle)
    }

    fun setBackgroundStyle(style: BackgroundStyle) = with(context) {
        val baseBackground = when (style) {
            BackgroundStyle.BLURRED -> getCutCornerDrawable(R.color.blurColor)
            BackgroundStyle.BORDERED -> getIdleDrawable()
        }

        background = addRipple(baseBackground)
    }

    fun onClick(action: (View) -> Unit) {
        setOnClickListener(action)
    }

    fun setState(
        imageLoader: ImageLoader,
        assetModel: AssetModel
    ) {
        with(assetModel) {
            binding.assetSelectorBalance.text = assetBalance
            binding.assetSelectorTokenName.text = tokenName
            binding.assetSelectorIcon.load(imageUrl, imageLoader)
        }
    }
}
