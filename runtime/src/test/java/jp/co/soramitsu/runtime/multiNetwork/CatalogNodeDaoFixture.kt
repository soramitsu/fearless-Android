package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/** Models scalar DAO calls for the existing registry-race JVM controls; native tests exercise SQLite. */
internal fun mockCatalogNodeDao(read: () -> List<ChainNodeLocal>, write: (List<ChainNodeLocal>) -> Unit): ChainDao {
    val ids = mutableMapOf<Pair<String, String>, Long>()
    fun id(node: ChainNodeLocal): Long = ids.getOrPut(node.chainId to node.url) { ids.size.toLong() + 1 }
    read().forEach { id(it) }
    return mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { invocation ->
        val args = invocation.arguments
        @Suppress("UNCHECKED_CAST")
        when (invocation.method.name) {
            "updateNodes", "updateNodesTransaction" -> invocation.callRealMethod()
            "selectedNodeRowId" -> read().filter { it.chainId == args[0] && it.isActive }
                .sortedWith(compareBy<ChainNodeLocal> { it.isDefault }.thenBy { id(it) }).firstOrNull()?.let { id(it) }
            "isCustomEndpointKeyReserved", "isCustomProjection" -> read().any { it.chainId == args[0] && it.url == args[1] && !it.isDefault }
            "insertCatalogNode" -> {
                val node = args[0] as ChainNodeLocal
                if (read().none { it.chainId == node.chainId && it.url == node.url }) {
                    id(node); write(read() + node)
                }
                Unit
            }
            "updateCatalogNodeName" -> {
                write(read().map { if (it.chainId == args[0] && it.url == args[1] && it.isDefault) it.copy(name = args[2] as String) else it })
                Unit
            }
            "deleteChainNodes" -> {
                val removed = args[0] as List<ChainNodeLocal>
                write(read().filter { node -> removed.none { it.chainId == node.chainId && it.url == node.url } })
                Unit
            }
            "nodeRowIdExists" -> read().any { it.chainId == args[0] && id(it) == args[1] }
            "nodeRowId" -> read().firstOrNull { it.chainId == args[0] && it.url == args[1] }?.let { id(it) }
            "firstDefaultNodeRowId" -> read().firstOrNull { it.chainId == args[0] && it.isDefault }?.let { id(it) }
            "setSelectedNodeRowId" -> {
                write(read().map { if (it.chainId == args[0]) it.copy(isActive = id(it) == args[1]) else it })
                Unit
            }
            else -> error("Unexpected DAO call ${invocation.method.name}")
        }
    })
}
