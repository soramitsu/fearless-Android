package jp.co.soramitsu.account.impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.feature_account_impl.databinding.FragmentProfileBinding

@AndroidEntryPoint
class ProfileFragment : BaseFragment<ProfileViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentProfileBinding

    override val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            accountView.setWholeClickListener { viewModel.accountActionsClicked() }

            aboutTv.setOnClickListener { viewModel.aboutClicked() }

            profileWallets.setOnClickListener { viewModel.walletsClicked() }
            languageWrapper.setOnClickListener { viewModel.languagesClicked() }
            changePinCodeTv.setOnClickListener { viewModel.changePinCodeClicked() }
            profileCurrency.setOnClickListener { viewModel.currencyClicked() }
        }
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.selectedAccountLiveData.observe { account ->
            account.name.let(binding.accountView::setTitle)
        }

        viewModel.accountIconLiveData.observe {
            binding.accountView.setAccountIcon(it.image)
        }

        viewModel.selectedLanguageLiveData.observe {
            binding.selectedLanguageTv.text = it.displayName
        }

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)

        viewModel.totalBalanceLiveData.observe {
            binding.accountView.setText(it)
        }

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)

        viewModel.selectedFiatLiveData.observe(binding.selectedCurrencyTv::setText)
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
}
