package jp.co.soramitsu.feature_staking_impl.presentation.validators.model

import android.graphics.drawable.PictureDrawable
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import java.math.BigDecimal

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

data class ValidatorModel(
    val slashed: Boolean,
    val identity: Identity?,
    val image: PictureDrawable,
    val address: String,
    private val apyDecimal: BigDecimal
) {

    val title = identity?.display ?: address

    val apy = (PERCENT_MULTIPLIER * apyDecimal).formatAsPercentage()
}

object ValidatorDiffCallback : DiffUtil.ItemCallback<ValidatorModel>() {

    override fun areItemsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem == newItem
    }
}
