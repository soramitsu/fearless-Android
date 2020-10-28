package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_about.emailText
import kotlinx.android.synthetic.main.fragment_about.githubText
import kotlinx.android.synthetic.main.fragment_about.telegramText
import kotlinx.android.synthetic.main.fragment_about.websiteText
import kotlinx.android.synthetic.main.fragment_about.backIv
import kotlinx.android.synthetic.main.fragment_about.emailWrapper
import kotlinx.android.synthetic.main.fragment_about.githubWrapper
import kotlinx.android.synthetic.main.fragment_about.privacyTv
import kotlinx.android.synthetic.main.fragment_about.telegramWrapper
import kotlinx.android.synthetic.main.fragment_about.termsTv
import kotlinx.android.synthetic.main.fragment_about.websiteWrapper

class ExportMnemonicFragment : BaseFragment<ExportMnemonicViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun initViews() {

    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportMnemonicFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportMnemonicViewModel) {

    }
}