package jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model

import android.graphics.drawable.Drawable
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId

data class CrowdloanModel(
    val parachainId: ParaId,
    val title: String,
    val description: String?,
    val icon: Icon,
    val timeRemaining: String,
    val raised: String
) {

    sealed class Icon {

        class FromLink(val data: String) : Icon()

        class FromDrawable(val data: Drawable) : Icon()
    }
}
