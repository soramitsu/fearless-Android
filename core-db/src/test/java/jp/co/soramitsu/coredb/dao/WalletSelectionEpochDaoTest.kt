package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class WalletSelectionEpochDaoTest {
    @Test fun `actual selection default method invalidates ABA snapshots and releases failed mutation`() = runBlocking {
        var selected = 1L
        var inside = false
        val implementation = Class.forName("jp.co.soramitsu.coredb.dao.MetaAccountDao\$DefaultImpls")
            .methods.single { it.name == "selectMetaAccount" }
        val dao = Proxy.newProxyInstance(MetaAccountDao::class.java.classLoader, arrayOf(MetaAccountDao::class.java)) { proxy, method, args ->
            when (method.name) {
                "selectMetaAccount" -> try { implementation.invoke(null, proxy, args!![0], args[1]) }
                    catch (error: InvocationTargetException) { throw error.cause!! }
                "selectExistingMetaAccount" -> {
                    inside = true
                    assertThrows(IllegalStateException::class.java) { WalletSelectionEpoch.snapshot() }
                    val requested = args!![0] as Long
                    if (requested == 99L) 0 else { selected = requested; 1 }
                }
                else -> error("Unexpected method ${method.name}")
            }
        } as MetaAccountDao
        val first = WalletSelectionEpoch.snapshot()
        dao.selectMetaAccount(2); dao.selectMetaAccount(1)
        assertEquals(1L, selected); assertTrue(inside); assertFalse(WalletSelectionEpoch.isCurrent(first))
        val returned = WalletSelectionEpoch.snapshot()
        assertTrue(runCatching { dao.selectMetaAccount(99) }.isFailure)
        assertEquals(1L, selected); assertFalse(WalletSelectionEpoch.isCurrent(returned))
        assertTrue(WalletSelectionEpoch.isCurrent(WalletSelectionEpoch.snapshot()))
    }
}
