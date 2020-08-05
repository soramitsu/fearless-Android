package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_account.toolbar


class ImportAccountFragment : BaseFragment<ImportAccountViewmodel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import_account, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
//        val country = arrayOf("India", "USA", "China", "Japan", "Other")
//        val aa = ArrayAdapter<Any?>(activity!!, R.layout.item_spinner, country)
//        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        //Setting the ArrayAdapter data on the Spinner
//        //Setting the ArrayAdapter data on the Spinner
//        spinner.adapter = aa
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .importAccountComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewmodel) {}
}