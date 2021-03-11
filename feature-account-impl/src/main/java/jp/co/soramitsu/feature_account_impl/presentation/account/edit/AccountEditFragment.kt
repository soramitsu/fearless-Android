package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import kotlinx.android.synthetic.main.fragment_accounts.addAccount
import kotlinx.android.synthetic.main.fragment_edit_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_edit_accounts.fearlessToolbar

class AccountEditFragment : BaseFragment<EditAccountsViewModel>(), EditAccountsAdapter.EditAccountItemHandler {
    private lateinit var adapter: EditAccountsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_edit_accounts, container, false)

    override fun initViews() {
        accountsList.setHasFixedSize(true)

        val dragHelper = createTouchHelper()
        dragHelper.attachToRecyclerView(accountsList)

        adapter = EditAccountsAdapter(this, dragHelper)
        accountsList.adapter = adapter

        fearlessToolbar.setRightActionClickListener {
            viewModel.doneClicked()
        }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        addAccount.setOnClickListener { viewModel.addAccountClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .editAccountsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: EditAccountsViewModel) {
        viewModel.accountListingLiveData.observe(adapter::submitListing)

        viewModel.deleteConfirmationLiveData.observeEvent(::showDeleteConfirmation)

        viewModel.unsyncedSwapLiveData.observe { payload ->
            payload?.let { adapter.unsyncedSwap(payload) }
        }
    }

    private fun showDeleteConfirmation(account: AccountModel) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.account_delete_confirmation_title)
            .setMessage(R.string.account_delete_confirmation_description)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                viewModel.deleteConfirmed(account)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun deleteClicked(accountModel: AccountModel) {
        viewModel.deleteClicked(accountModel)
    }

    private fun createTouchHelper(): ItemTouchHelper {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            var dragFrom: Int? = null
            var dragTo: Int? = null

            override fun isLongPressDragEnabled() = false

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                if (dragFrom == null) {
                    dragFrom = from
                }

                dragTo = to

                viewModel.onItemDrag(from, to)

                return false
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                if (dragFrom != null && dragTo != null) {
                    viewModel.onItemDrop()
                }

                dragFrom = null
                dragTo = null
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }
        }

        return ItemTouchHelper(callback)
    }
}