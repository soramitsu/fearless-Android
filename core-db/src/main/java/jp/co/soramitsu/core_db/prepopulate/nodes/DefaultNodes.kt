package jp.co.soramitsu.core_db.prepopulate.nodes

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.model.NodeLocal

val LATEST_DEFAULT_NODES = listOf(
    // --- kusama ----
    NodeLocal(
        "Kusama OnFinality Node",
        "wss://kusama.api.onfinality.io/ws?apikey=0b2faaa5-3ef1-48ea-bf75-8f3a0cedb1ef",
        Node.NetworkType.KUSAMA.ordinal,
        true
    ),
    NodeLocal(
        "Kusama Parity Node",
        "wss://kusama-rpc.polkadot.io",
        Node.NetworkType.KUSAMA.ordinal,
        true
    ),
    NodeLocal(
        "Kusama Patract Node",
        "wss://pub.elara.patract.io/kusama",
        Node.NetworkType.KUSAMA.ordinal,
        true
    ),
    // --- polkadot ----
    NodeLocal(
        "Polkadot OnFinality Node",
        "wss://polkadot.api.onfinality.io/ws?apikey=0b2faaa5-3ef1-48ea-bf75-8f3a0cedb1ef",
        Node.NetworkType.POLKADOT.ordinal,
        true
    ),
    NodeLocal(
        "Polkadot Parity Node",
        "wss://rpc.polkadot.io",
        Node.NetworkType.POLKADOT.ordinal,
        true
    ),
    NodeLocal(
        "Polkadot Patract Node",
        "wss://pub.elara.patract.io/polkadot",
        Node.NetworkType.POLKADOT.ordinal,
        true
    ),
    // --- westend ----
    NodeLocal(
        "Westend Parity Node",
        "wss://westend-rpc.polkadot.io",
        Node.NetworkType.WESTEND.ordinal,
        true
    ),

    // -- rococo community stand
    NodeLocal(
        "Laminar Node",
        "wss://rococo-community-rpc.laminar.codes/ws",
        Node.NetworkType.ROCOCO.ordinal,
        true
    )
)

fun defaultNodesInsertQuery(nodesList: List<NodeLocal>): String {
    return "insert into nodes (name, link, networkType, isDefault, isActive) values " +
        nodesList.joinToString {
            node ->
            "('${node.name}', '${node.link}', ${node.networkType}, ${if (node.isDefault) 1 else 0}, ${if (node.isActive) 1 else 0})"
        }
}
