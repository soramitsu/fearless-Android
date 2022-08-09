package jp.co.soramitsu.staking.impl.presentation.validators.change

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.staking.api.domain.model.Collator

data class CollatorModel(
    val accountIdHex: String,
    val slashed: Boolean,
    val image: PictureDrawable,
    val address: String,
    val scoring: Scoring?,
    val title: String,
    val isChecked: Boolean?,
    val collator: Collator
) {

    sealed class Scoring {
        class OneField(val field: String) : Scoring()

        class TwoFields(val primary: String, val secondary: String?) : Scoring()

        val apr: String by lazy {
            when (this) {
                is OneField -> field
                is TwoFields -> primary
            }
        }
    }
}
