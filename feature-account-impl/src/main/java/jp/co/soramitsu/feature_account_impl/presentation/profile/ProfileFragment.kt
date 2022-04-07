package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_profile.aboutTv
import kotlinx.android.synthetic.main.fragment_profile.accountView
import kotlinx.android.synthetic.main.fragment_profile.changePinCodeTv
import kotlinx.android.synthetic.main.fragment_profile.languageWrapper
import kotlinx.android.synthetic.main.fragment_profile.profileCurrency
import kotlinx.android.synthetic.main.fragment_profile.profileWallets
import kotlinx.android.synthetic.main.fragment_profile.selectedCurrencyTv
import kotlinx.android.synthetic.main.fragment_profile.selectedLanguageTv
import kotlinx.android.synthetic.main.fragment_profile.walletConnect

class ProfileFragment : BaseFragment<ProfileViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun initViews() {
        accountView.setWholeClickListener { viewModel.accountActionsClicked() }

        aboutTv.setOnClickListener { viewModel.aboutClicked() }

        profileWallets.setOnClickListener { viewModel.walletsClicked() }
        languageWrapper.setOnClickListener { viewModel.languagesClicked() }
        changePinCodeTv.setOnClickListener { viewModel.changePinCodeClicked() }
        profileCurrency.setOnClickListener { viewModel.currencyClicked() }
        walletConnect.setOnClickListener { viewModel.walletConnect() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .profileComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.selectedAccountLiveData.observe { account ->
            account.name.let(accountView::setTitle)
        }

        viewModel.accountIconLiveData.observe {
            accountView.setAccountIcon(it.image)
        }

        viewModel.selectedLanguageLiveData.observe {
            selectedLanguageTv.text = it.displayName
        }

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)

        viewModel.totalBalanceLiveData.observe {
            accountView.setText(it)
        }

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)

        viewModel.selectedFiatLiveData.observe(selectedCurrencyTv::setText)

        viewModel.openScanQrCodeLiveData.observe { openQrCodeScanner() }
    }

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(requireContext(), imageLoader, payload, viewModel::onFiatSelected).show()
    }

    private fun showAccountActions(payload: ExternalAccountActions.Payload) {
        ProfileActionsSheet(
            requireContext(),
            payload,
            viewModel::copyAddressClicked,
            viewModel::viewExternalClicked,
            viewModel::walletsClicked
        ).show()
    }

    private fun openQrCodeScanner(){

    }
}
