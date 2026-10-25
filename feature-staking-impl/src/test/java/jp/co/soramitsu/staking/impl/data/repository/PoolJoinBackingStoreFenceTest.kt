package jp.co.soramitsu.staking.impl.data.repository

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.math.BigInteger

/** Actual PreferencesImpl wrappers over an explicitly modeled SharedPreferences commit failure. */
class PoolJoinBackingStoreFenceTest {
    @Test fun `failed disk acknowledgement fences recreated wrappers sharing the same memory store`() {
        val scope = PoolJoinJournal.Scope(7, "91".repeat(32), "05".repeat(32))
        val values = mutableMapOf<String, String>()
        val puts = mutableMapOf<String, String>()
        val removals = mutableSetOf<String>()
        var failCommit = false
        val shared = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(shared.edit()).thenAnswer { puts.clear(); removals.clear(); editor }
        whenever(shared.getString(any(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        whenever(editor.putString(any(), any())).thenAnswer { puts[it.getArgument(0)] = it.getArgument(1); editor }
        whenever(editor.remove(any())).thenAnswer { removals += it.getArgument<String>(0); editor }
        whenever(editor.commit()).thenAnswer {
            removals.forEach(values::remove)
            values.putAll(puts)
            !failCommit
        }
        val first = PoolJoinJournal(PreferencesImpl(shared))
        val anchor = FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32))
        val payment = first.retain(scope, BigInteger.TEN, BigInteger.ONE, "0x0102", "12".repeat(32), anchor)
        val inclusion = FinalizedExtrinsicReader.Inclusion("0x" + scope.chainId, payment.transactionHash,
            "0x" + "21".repeat(32), BigInteger.ONE, 0, "0x" + "22".repeat(32), BigInteger.TWO)
        failCommit = true
        assertThrows(IllegalStateException::class.java) { first.finalize(payment, inclusion) }
        assertNull(values[scope.key])
        val recreated = PoolJoinJournal(PreferencesImpl(shared))
        assertThrows(IllegalStateException::class.java) { recreated.requireAvailable(scope) }
        assertThrows(IllegalStateException::class.java) { recreated.readFinalized(scope, payment.attemptId) }
        recreated.requireAvailable(scope.copy(walletId = 8))
        PoolJoinPreferencesFixture().journal().requireAvailable(scope)
    }
}
