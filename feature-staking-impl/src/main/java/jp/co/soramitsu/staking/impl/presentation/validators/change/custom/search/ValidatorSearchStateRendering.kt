package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search

import android.view.View
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSearchCustomValidatorsBinding

/** Renders the production search layout without owning the Fragment or its ViewModel lifecycle. */
fun FragmentSearchCustomValidatorsBinding.renderValidatorSearchState(
    state: SearchBlockProducersState,
    retry: () -> Unit,
    submitList: (List<SearchBlockProducerModel>) -> Unit
) {
    searchCustomValidatorsList.setVisible(state is SearchBlockProducersState.Success, falseState = View.INVISIBLE)
    searchCustomValidatorProgress.setVisible(state is SearchBlockProducersState.Loading, falseState = View.INVISIBLE)
    val retryRequired = state is SearchBlockProducersState.Failed || state is SearchBlockProducersState.RefreshRequired
    searchCustomValidatorsPlaceholder.setVisible(
        retryRequired || state is SearchBlockProducersState.NoResults || state is SearchBlockProducersState.NoInput
    )
    val retryListener = if (retryRequired) View.OnClickListener { retry() } else null
    searchCustomValidatorsPlaceholder.setOnClickListener(retryListener)
    searchCustomValidatorsPlaceholder.isClickable = retryRequired
    searchCustomValidatorsPlaceholder.isFocusable = retryRequired
    searchCustomValidatorListHeader.setVisible(state is SearchBlockProducersState.Success)

    when (state) {
        SearchBlockProducersState.NoInput -> {
            searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_placeholder, 0, 0)
            searchCustomValidatorsPlaceholder.text = root.context.getString(R.string.common_search_start_title)
        }
        SearchBlockProducersState.NoResults -> {
            searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_no_search_results, 0, 0)
            searchCustomValidatorsPlaceholder.text = root.context.getString(R.string.staking_validator_search_empty_title)
        }
        SearchBlockProducersState.RefreshRequired -> {
            searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_placeholder, 0, 0)
            searchCustomValidatorsPlaceholder.text = root.context.getString(R.string.common_retry)
        }
        is SearchBlockProducersState.Failed -> {
            searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_no_search_results, 0, 0)
            searchCustomValidatorsPlaceholder.text = "${state.message}\n${root.context.getString(R.string.common_retry)}"
        }
        SearchBlockProducersState.Loading -> {}
        is SearchBlockProducersState.Success -> {
            searchCustomValidatorAccounts.text = state.headerTitle

            submitList(state.blockProducers)
        }
    }
}
