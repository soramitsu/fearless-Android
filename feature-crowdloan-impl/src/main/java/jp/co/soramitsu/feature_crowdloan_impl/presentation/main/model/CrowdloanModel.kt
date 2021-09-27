package jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model

import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class CrowdloanStatusModel(
    val text: String,
    @ColorRes val textColorRes: Int
)

data class CrowdloanModel(
    val relaychainId: ChainId,
    val parachainId: ParaId,
    val title: String,
    val description: String,
    val icon: Icon,
    val raised: String,
    val state: State,
    val myContribution: String?
) {

    sealed class State {
        object Finished : State()

        data class Active(val timeRemaining: String) : State()
    }

    sealed class Icon {

        class FromLink(val data: String) : Icon()

        class FromDrawable(val data: Drawable) : Icon()
    }
}
