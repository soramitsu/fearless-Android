package jp.co.soramitsu.users.presentation.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_user_api.domain.model.User
import jp.co.soramitsu.users.R
import kotlinx.android.synthetic.main.item_user.view.firstNameTv
import kotlinx.android.synthetic.main.item_user.view.lastNameTv

class UsersAdapter(
    private val userClickListener: (User) -> Unit
) : ListAdapter<User, UserViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), userClickListener)
    }
}

class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(user: User, userClickListener: (User) -> Unit) {
        with(itemView) {
            firstNameTv.text = user.firstName
            lastNameTv.text = user.lastName

            setOnClickListener {
                userClickListener(user)
            }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}