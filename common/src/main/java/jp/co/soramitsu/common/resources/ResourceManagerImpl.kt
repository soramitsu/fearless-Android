package jp.co.soramitsu.common.resources

import android.content.Context
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.di.scope.ApplicationScope
import javax.inject.Inject

@ApplicationScope
class ResourceManagerImpl @Inject constructor(
    private val context: Context
) : ResourceManager {

    override fun getString(res: Int): String {
        return context.getString(res)
    }

    override fun getColor(res: Int): Int {
        return ContextCompat.getColor(context, res)
    }

    override fun getQuantityString(id: Int, quantity: Int): String {
        return context.resources.getQuantityString(id, quantity)
    }
}