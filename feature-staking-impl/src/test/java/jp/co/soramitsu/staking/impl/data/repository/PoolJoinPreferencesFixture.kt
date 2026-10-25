package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.data.storage.Preferences
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.ConcurrentHashMap

/** Explicitly in-memory public transaction preferences; not Android disk durability evidence. */
class PoolJoinPreferencesFixture {
    private val storageIdentity = Any()
    val values = ConcurrentHashMap<String, String>()
    @Volatile var failCommit = false
    @Volatile var beforeCommit: (() -> Unit)? = null
    val preferences = mock<Preferences> {
        on { durableStorageIdentity } doReturn storageIdentity
        on { getString(any()) } doAnswer { values[it.getArgument<String>(0)] }
        on { replaceStringsDurably(any(), any()) } doAnswer { call ->
            beforeCommit?.invoke()
            if (failCommit) false else {
                call.getArgument<Set<String>>(1).forEach(values::remove)
                values.putAll(call.getArgument<Map<String, String>>(0))
                true
            }
        }
    }
    fun journal() = PoolJoinJournal(preferences)
}
