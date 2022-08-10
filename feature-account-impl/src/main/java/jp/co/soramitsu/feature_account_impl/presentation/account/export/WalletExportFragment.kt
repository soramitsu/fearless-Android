package jp.co.soramitsu.feature_account_impl.presentation.account.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.feature_account_impl.databinding.FragmentWalletExportBinding
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import javax.inject.Inject

const val META_ID_KEY = "META_ID_KEY"

@AndroidEntryPoint
class WalletExportFragment : BaseFragment<WalletExportViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentWalletExportBinding

    override val viewModel: WalletExportViewModel by viewModels()

    companion object {
        fun getBundle(metaAccountId: Long) = bundleOf(META_ID_KEY to metaAccountId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWalletExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            exportWalletToolbar.setHomeButtonListener {
                viewModel.backClicked()
            }
            continueBtn.setOnClickListener {
                if (amountsWithOneKeyView.isSelected) {
                    viewModel.continueClicked(AccountInChain.From.META_ACCOUNT)
                }
            }
        }
    }

    override fun subscribe(viewModel: WalletExportViewModel) {
        viewModel.accountNameLiveData.observe {
            binding.exportWalletInfoView.setTitle(it)
        }
        viewModel.accountIconLiveData.observe {
            binding.exportWalletInfoView.setAccountIcon(it)
        }
        viewModel.totalBalanceLiveData.observe {
            binding.exportWalletInfoView.setText(it)
        }

        viewModel.amountsWithOneKeyAmountBadgeLiveData.observe {
            binding.amountsWithOneKeyView.setBadgeText(it)
        }

        viewModel.amountsWithOneKeyChainNameLiveData.observe {
            binding.amountsWithOneKeyView.setChainName(it)
        }

        viewModel.amountsWithOneKeyChainIconLiveData.observe {
            binding.amountsWithOneKeyView.loadChainIcon(it, imageLoader)
        }
    }
}
