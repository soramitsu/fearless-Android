package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.Toolbar
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.AcalaReferralFlowBinding
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralContributeView

class AcalaContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle, R.layout.acala_referral_flow) {

    var icon: Drawable? = null

    private val binding: AcalaReferralFlowBinding = AcalaReferralFlowBinding.bind(this)

    init {
        icon = AppCompatResources.getDrawable(context, R.drawable.ic_close)
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        super.bind(viewState, scope)

        require(viewState is AcalaContributeViewState)
        viewState.privacyAcceptedFlow.value = true // agreement for acala on contribution screen
        binding.referralReferralCodeInput.hint = context.getString(R.string.crowdloan_referral_code_hint)

        if (viewState.isAcala) {
            icon?.let {
                this.rootView.findViewById<Toolbar>(R.id.customContributeToolbar)?.setHomeButtonIcon(it)
            }
        }

        findViewById<InputField>(R.id.referralEmailInput)?.content?.bindTo(viewState.enteredEmailFlow, scope)
        findViewById<SwitchMaterial>(R.id.referralEmailSwitch)?.bindTo(viewState.emailAgreedFlow, scope)
    }
}
