package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings
import org.mockito.stubbing.Answer

/** Branch guards exercise the actual DAO methods; native tests qualify SQL and transaction rollback. */
class CustomEndpointLifecycleDaoTest {
    private val node = ChainNodeLocal("chain", "https://custom.invalid", "User", false, false)

    @Test fun `noneditable projection exits before ownership reads or writes`() = runBlocking {
        val calls = mutableListOf<String>()
        val dao = realDao { call ->
            calls += call.method.name
            when (call.method.name) {
                "updateNode" -> call.callRealMethod()
                "isEditableNode" -> false
                else -> error("Unexpected mutation or ownership access")
            }
        }
        assertEquals(0, dao.updateNode(node.chainId, node.url, "Changed", node.url))
        assertEquals(listOf("isEditableNode"), calls)
    }

    @Test fun `exhausted edit revision fails before either store changes`() = runBlocking {
        val dao = realDao { call ->
            when (call.method.name) {
                "updateNode" -> call.callRealMethod()
                "isEditableNode" -> true
                "customEndpointId" -> 7L
                "customEndpointRevision" -> Long.MAX_VALUE
                else -> error("Unexpected mutation")
            }
        }
        val failure = runCatching { dao.updateNode(node.chainId, node.url, "Changed", node.url) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("Custom endpoint revision cannot advance", failure!!.message)
    }

    @Test fun `live key cannot be acquired again by explicit add`() = runBlocking {
        val dao = realDao { call ->
            when (call.method.name) {
                "insertChainNode" -> call.callRealMethod()
                "customEndpointId" -> 7L
                "isDeletedCustomEndpoint" -> false
                else -> error("Unexpected mutation")
            }
        }
        assertEquals("Custom endpoint key is already owned", runCatching { dao.insertChainNode(node) }.exceptionOrNull()!!.message)
    }

    @Test fun `missing selection is checked with a scalar before clearing the previous choice`() = runBlocking {
        val calls = mutableListOf<String>()
        val dao = realDao { call ->
            calls += call.method.name
            when (call.method.name) {
                "selectNode" -> call.callRealMethod()
                "nodeRowId" -> null
                else -> error("Unexpected selection mutation or payload read")
            }
        }
        assertTrue(runCatching { dao.selectNode(node.chainId, node.url) }.isFailure)
        assertEquals(listOf("nodeRowId"), calls)
    }

    @Test fun `empty parent deletion does not issue SQL`() = runBlocking {
        val dao = realDao { error("Empty deletion must not issue SQL") }
        dao.deleteChains(emptyList())
    }

    @Test fun `repeated explicit deletion retains the existing tombstone revision`() = runBlocking {
        val calls = mutableListOf<String>()
        val dao = realDao { call ->
            calls += call.method.name
            when (call.method.name) {
                "deleteNode" -> call.callRealMethod()
                "customEndpointId" -> 7L
                "isDeletedCustomEndpoint" -> true
                "deleteNodeProjection" -> Unit
                else -> error("Unexpected tombstone mutation")
            }
        }
        dao.deleteNode(node.chainId, node.url)
        assertEquals(listOf("customEndpointId", "isDeletedCustomEndpoint", "deleteNodeProjection"), calls)
    }

    private fun realDao(query: Answer<Any?>): ChainDao = mock(
        ChainDao::class.java,
        withSettings().useConstructor().defaultAnswer { call ->
            if (call.method.name in setOf(
                "getEndpointMutationLane", "updateNode", "selectNode", "deleteNode", "insertChainNode", "deleteChains"
            ) || call.method.name.endsWith("Transaction")) {
                call.callRealMethod()
            } else {
                query.answer(call)
            }
        }
    )
}
