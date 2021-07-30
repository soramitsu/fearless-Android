package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorAccounts
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorListHeader
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorProgress
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorsContainer
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorsField
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorsList
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorsPlaceholder
import kotlinx.android.synthetic.main.fragment_search_custom_validators.searchCustomValidatorsToolbar

class SearchCustomValidatorsFragment : BaseFragment<SearchCustomValidatorsViewModel>(), ValidatorsAdapter.ItemHandler {

    private val adapter: ValidatorsAdapter by lazy(LazyThreadSafetyMode.NONE) {
        ValidatorsAdapter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search_custom_validators, container, false)
    }

    override fun initViews() {
        searchCustomValidatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        searchCustomValidatorsList.adapter = adapter
        searchCustomValidatorsList.setHasFixedSize(true)

        searchCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        searchCustomValidatorsToolbar.setRightActionClickListener {
            viewModel.doneClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .searchCustomValidatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SearchCustomValidatorsViewModel) {
        viewModel.screenState.observe {
            searchCustomValidatorsList.setVisible(it is SearchValidatorsState.Success, falseState = View.INVISIBLE)
            searchCustomValidatorProgress.setVisible(it is SearchValidatorsState.Loading, falseState = View.INVISIBLE)
            searchCustomValidatorsPlaceholder.setVisible(it is SearchValidatorsState.NoResults || it is SearchValidatorsState.NoInput)
            searchCustomValidatorListHeader.setVisible(it is SearchValidatorsState.Success)

            when (it) {
                SearchValidatorsState.NoInput -> {
                    searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_placeholder, 0, 0)
                    searchCustomValidatorsPlaceholder.text = getString(R.string.common_search_start_title)
                }
                SearchValidatorsState.NoResults -> {
                    searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_no_search_results, 0, 0)
                    searchCustomValidatorsPlaceholder.text = getString(R.string.common_search_no_results)
                }
                SearchValidatorsState.Loading -> {}
                is SearchValidatorsState.Success -> {
                    searchCustomValidatorAccounts.text = it.headerTitle

                    adapter.submitList(it.validators)
                }
            }
        }

        searchCustomValidatorsField.bindTo(viewModel.enteredQuery, viewLifecycleOwner.lifecycleScope)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }

    override fun validatorClicked(validatorModel: ValidatorModel) {
        viewModel.validatorClicked(validatorModel)
    }
}
