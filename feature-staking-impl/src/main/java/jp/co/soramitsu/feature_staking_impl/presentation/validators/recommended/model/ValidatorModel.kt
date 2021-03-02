package jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model

import android.graphics.drawable.PictureDrawable
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.feature_staking_api.domain.model.Identity

data class ValidatorModel(
    val accountIdHex: String,
    val slashed: Boolean,
    val identity: Identity?,
    val image: PictureDrawable,
    val address: String,
    val apy: String,
    val title: String
)

object ValidatorDiffCallback : DiffUtil.ItemCallback<ValidatorModel>() {

    override fun areItemsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem == newItem
    }
}