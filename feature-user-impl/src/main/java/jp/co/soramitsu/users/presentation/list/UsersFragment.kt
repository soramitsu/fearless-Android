package jp.co.soramitsu.users.presentation.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_user_api.di.UserFeatureApi
import jp.co.soramitsu.users.R
import jp.co.soramitsu.users.di.UserFeatureComponent
import kotlinx.android.synthetic.main.fragment_users.toolbar
import kotlinx.android.synthetic.main.fragment_users.usersRv

class UsersFragment : BaseFragment<UsersViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_users, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<UserFeatureComponent>(this, UserFeatureApi::class.java)
            .usersComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.users_title))

        usersRv.layoutManager = LinearLayoutManager(activity!!)
        usersRv.setHasFixedSize(true)
    }

    override fun subscribe(viewModel: UsersViewModel) {
        viewModel.usersLiveData.observe(this, Observer {
            if (usersRv.adapter == null) {
                usersRv.adapter = UsersAdapter { viewModel.userClicked(it) }
            }
            (usersRv.adapter as UsersAdapter).submitList(it)
        })

        viewModel.getUsers()
    }
}