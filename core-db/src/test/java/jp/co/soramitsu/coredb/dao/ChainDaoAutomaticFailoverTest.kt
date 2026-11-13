package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/** Actual automatic-failover writer decisions over controlled scalar row replies; Room supplies the SQL. */
class ChainDaoAutomaticFailoverTest {
    @Test fun deadFirstCatalogNodeMovesToTheNextCatalogNodeByRowId() = runBlocking<Unit> {
        val f = Fixture(
            Row(1, "a", FIRST, isActive = true), Row(2, "b", FIRST, isActive = true),
            Row(3, "a", SECOND), Row(4, "a", THIRD), Row(5, "b", SECOND)
        )
        val operation = checkNotNull(f.lane.admitObserved(f.observe()))
        assertTrue(f.switch(operation, FIRST))
        assertEquals(SECOND, f.selectedUrl("a"))
        assertEquals(listOf("a" to 3L), f.selections)
        // Another chain sharing the URL keeps its own selection.
        assertEquals(FIRST, f.selectedUrl("b"))
        assertFalse(f.lane.isCurrent(operation))
    }

    @Test fun lastCatalogNodeWrapsAroundToTheFirst() = runBlocking<Unit> {
        val f = Fixture(Row(1, "a", FIRST), Row(2, "a", SECOND), Row(3, "a", CUSTOM, isDefault = false), Row(4, "a", THIRD, isActive = true))
        assertTrue(f.switch(checkNotNull(f.lane.admitObserved(f.observe())), THIRD))
        assertEquals(FIRST, f.selectedUrl("a"))
        assertEquals(listOf("a" to 1L), f.selections)
    }

    @Test fun customNodesAreNeitherReplacedNorChosen() = runBlocking<Unit> {
        // A selected custom node is the user's own choice, however often it fails.
        val chosen = Fixture(Row(1, "a", FIRST), Row(2, "a", SECOND), Row(3, "a", CUSTOM, isActive = true, isDefault = false))
        assertFalse(chosen.switch(checkNotNull(chosen.lane.admitObserved(chosen.observe())), CUSTOM))
        assertEquals(CUSTOM, chosen.selectedUrl("a"))
        assertTrue(chosen.selections.isEmpty())
        // A failing catalog node never moves to a custom node the user only added.
        val added = Fixture(Row(1, "a", FIRST, isActive = true), Row(2, "a", CUSTOM, isDefault = false))
        val operation = checkNotNull(added.lane.admitObserved(added.observe()))
        assertFalse(added.switch(operation, FIRST))
        assertEquals(FIRST, added.selectedUrl("a"))
        assertTrue(added.selections.isEmpty())
        assertFalse(added.lane.isCurrent(operation))
    }

    @Test fun aSelectionThatAlreadyMovedIsKept() = runBlocking<Unit> {
        val f = Fixture(Row(1, "a", FIRST), Row(2, "a", SECOND, isActive = true), Row(3, "a", THIRD))
        assertFalse(f.switch(checkNotNull(f.lane.admitObserved(f.observe())), FIRST))
        assertFalse(f.switch(checkNotNull(f.lane.admitObserved(f.observe())), "wss://removed.invalid"))
        assertEquals(SECOND, f.selectedUrl("a"))
        assertTrue(f.selections.isEmpty())
    }

    @Test fun autoSelectTurnedOffBeforeTheWriteKeepsTheSelection() = runBlocking<Unit> {
        val f = Fixture(Row(1, "a", FIRST, isActive = true), Row(2, "a", SECOND))
        val operation = checkNotNull(f.lane.admitObserved(f.observe()))
        assertFalse(f.switch(operation, FIRST, allowed = false))
        assertEquals(FIRST, f.selectedUrl("a"))
        assertTrue(f.selections.isEmpty())
        assertFalse(f.lane.isCurrent(operation))
    }

    @Test fun anIntentAfterTheObservationPreventsAdmission() = runBlocking<Unit> {
        val f = Fixture(Row(1, "a", FIRST, isActive = true), Row(2, "a", SECOND))
        val observed = f.observe()
        // The user picks a node (or a catalog sync starts) after the failure was observed.
        val explicit = f.lane.admit(listOf("a"))
        assertNull(f.lane.admitObserved(observed))
        f.lane.abandon(explicit)
        assertNull(f.lane.admitObserved(observed))
        val catalogObserved = f.observe()
        f.lane.abandon(f.lane.admitCatalogReplacement())
        assertNull(f.lane.admitObserved(catalogObserved))
        assertTrue(f.selections.isEmpty())
    }

    @Test fun anIntentAdmittedBeforeTheWriteSupersedesTheSwitch() = runBlocking<Unit> {
        for (admitNewer in listOf<(EndpointMutationLane) -> EndpointMutationLane.Operation>(
            { it.admit(listOf("a")) }, { it.admitCatalogReplacement() }
        )) {
            val f = Fixture(Row(1, "a", FIRST, isActive = true), Row(2, "a", SECOND))
            val operation = checkNotNull(f.lane.admitObserved(f.observe()))
            val newer = admitNewer(f.lane)
            assertTrue(runCatching { f.switch(operation, FIRST) }.exceptionOrNull() is IllegalStateException)
            assertEquals(FIRST, f.selectedUrl("a"))
            assertTrue(f.selections.isEmpty())
            assertTrue(f.lane.isCurrent(newer))
        }
    }

    @Test fun anIntentAdmittedWhileTheWriterRunsRollsTheSwitchBack() = runBlocking<Unit> {
        val f = Fixture(Row(1, "a", FIRST, isActive = true), Row(2, "a", SECOND))
        val operation = checkNotNull(f.lane.admitObserved(f.observe()))
        var explicit: EndpointMutationLane.Operation? = null
        f.duringWriter = { explicit = f.lane.admit(listOf("a")) }
        assertTrue(runCatching { f.switch(operation, FIRST) }.exceptionOrNull() is IllegalStateException)
        assertEquals(FIRST, f.selectedUrl("a"))
        assertTrue(f.selections.isEmpty())
        assertTrue(f.lane.isCurrent(checkNotNull(explicit)))
    }

    private class Row(val rowId: Long, val chainId: String, val url: String, var isActive: Boolean = false, val isDefault: Boolean = true)

    private class Fixture(vararg initial: Row) {
        val rows = initial.toMutableList()
        val selections = mutableListOf<Pair<String, Long?>>()
        var duringWriter: () -> Unit = {}
        val dao: ChainDao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { call ->
            fun chain(rowsOf: String = call.getArgument(0)) = rows.filter { it.chainId == rowsOf }
            when (call.method.name) {
                "getEndpointMutationLane", "readEndpointRuntime", "selectNextCatalogNode" -> call.callRealMethod()
                // Room's generated transaction rolls every statement back when the body throws.
                "selectNextCatalogNodeTransaction" -> {
                    val active = rows.map { it.isActive }
                    val written = selections.size
                    try {
                        call.callRealMethod()
                    } catch (failure: Throwable) {
                        rows.forEachIndexed { index, row -> row.isActive = active[index] }
                        while (selections.size > written) selections.removeAt(selections.lastIndex)
                        throw failure
                    }
                }
                "getJoinChainInfo" -> null
                "selectedNodeRowId" -> chain().filter { it.isActive }
                    .sortedWith(compareBy<Row>({ it.isDefault }, { it.rowId })).firstOrNull()?.rowId
                "nodeRowId" -> chain().firstOrNull { it.url == call.getArgument<String>(1) }?.rowId
                "isCatalogNodeRowId" -> chain().any { it.rowId == call.getArgument<Long>(1) && it.isDefault }
                "nextCatalogNodeRowId" -> {
                    duringWriter()
                    chain().filter { it.isDefault && it.rowId > call.getArgument<Long>(1) }.minOfOrNull { it.rowId }
                }
                "firstDefaultNodeRowId" -> chain().filter { it.isDefault }.minOfOrNull { it.rowId }
                "setSelectedNodeRowId" -> {
                    val id = call.getArgument<Long?>(1)
                    selections += call.getArgument<String>(0) to id
                    chain().forEach { it.isActive = it.rowId == id }
                    Unit
                }
                else -> error("Unexpected DAO call: ${call.method.name}")
            }
        })
        val lane: EndpointMutationLane get() = dao.endpointMutationLane

        /** The pool admits an automatic switch only against the fresh read that configured the failing node. */
        suspend fun observe() = dao.readEndpointRuntime("a").version
        suspend fun switch(operation: EndpointMutationLane.Operation, unreachableUrl: String, allowed: Boolean = true) =
            dao.selectNextCatalogNode(operation, "a", unreachableUrl) { allowed }
        fun selectedUrl(chainId: String) = rows.single { it.chainId == chainId && it.isActive }.url
    }

    private companion object {
        const val FIRST = "wss://first.invalid"
        const val SECOND = "wss://second.invalid"
        const val THIRD = "wss://third.invalid"
        const val CUSTOM = "wss://custom.invalid"
    }
}
