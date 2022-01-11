package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.Toolbar
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.android.synthetic.main.view_referral_flow.view.referralReferralCodeInput

class AcalaContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle, R.layout.acala_referral_flow) {

    var icon: Drawable? = null

    init {
        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)
        icon = AppCompatResources.getDrawable(context, R.drawable.ic_close)
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        super.bind(viewState, scope)

        require(viewState is AcalaContributeViewState)
        viewState.privacyAcceptedFlow.value = true // agreement for acala on contribution screen
        referralReferralCodeInput.hint = context.getString(R.string.crowdloan_referral_code_hint)

        if (viewState.isAcala) {
            icon?.let {
                this.rootView.findViewById<Toolbar>(R.id.customContributeToolbar)?.setHomeButtonIcon(it)
            }
        }

        findViewById<InputField>(R.id.referralEmailInput)?.content?.bindTo(viewState.enteredEmailFlow, scope)
        findViewById<SwitchMaterial>(R.id.referralEmailSwitch)?.bindTo(viewState.emailAgreedFlow, scope)
    }
}
