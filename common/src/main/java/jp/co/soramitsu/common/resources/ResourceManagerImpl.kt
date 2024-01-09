package jp.co.soramitsu.common.resources

import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.getDrawableCompat
import javax.inject.Singleton
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Singleton
class ResourceManagerImpl(
    private val contextManager: ContextManager
) : ResourceManager {
    override fun getString(res: Int): String {
        return contextManager.getContext().getString(res)
    }

    override fun getString(res: Int, vararg arguments: Any): String {
        return contextManager.getContext().getString(res, *arguments)
    }

    override fun getColor(res: Int): Int {
        return ContextCompat.getColor(contextManager.getContext(), res)
    }

    override fun getQuantityString(id: Int, quantity: Int): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity)
    }

    override fun getQuantityString(id: Int, quantity: Int, vararg arguments: Any): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity, *arguments)
    }

    override fun measureInPx(dp: Int): Int {
        val px = contextManager.getContext().resources.displayMetrics.density * dp

        return px.toInt()
    }

    override fun formatDate(timestamp: Long): String {
        return timestamp.formatDateTime()
    }

    override fun formatDuration(elapsedTime: Long): String {
        val inDays = elapsedTime.daysFromMillis().toInt()

        return when {
            inDays > 0 -> getQuantityString(R.plurals.common_days_format, inDays, inDays)
            else -> {
                val inSeconds = elapsedTime.toDuration(DurationUnit.MILLISECONDS).toLong(DurationUnit.SECONDS)

                DateUtils.formatElapsedTime(inSeconds)
            }
        }
    }

    override fun getDrawable(id: Int): Drawable {
        return contextManager.getContext().getDrawableCompat(id)
    }
}
