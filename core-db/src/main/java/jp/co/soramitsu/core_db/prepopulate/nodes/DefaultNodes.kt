package jp.co.soramitsu.core_db.prepopulate.nodes

import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_account_api.domain.model.Node

class DefaultNodes {

    private val KUSAMA_PARITY = NodeLocal(
        "Kusama Parity Node",
        "wss://kusama-rpc.polkadot.io",
        Node.NetworkType.KUSAMA.ordinal,
        true
    )

    private val KUSAMA_WEB3 = NodeLocal(
        "Kusama, Web3 Foundation node",
        "wss://cc3-5.kusama.network",
        Node.NetworkType.KUSAMA.ordinal,
        true
    )

    private val POLKADOT_PARITY = NodeLocal(
        "Polkadot Parity Node",
        "wss://rpc.polkadot.io",
        Node.NetworkType.POLKADOT.ordinal,
        true
    )

    private val POLKADOT_WEB3 = NodeLocal(
        "Polkadot, Web3 Foundation node",
        "wss://cc1-1.polkadot.network",
        Node.NetworkType.POLKADOT.ordinal,
        true
    )

    private val WESTED_PARITY = NodeLocal(
        "Westend Parity Node",
        "wss://westend-rpc.polkadot.io",
        Node.NetworkType.WESTEND.ordinal,
        true
    )

    private val DEFAULT_NODES_LIST = listOf(KUSAMA_PARITY, KUSAMA_WEB3, POLKADOT_PARITY, POLKADOT_WEB3, WESTED_PARITY)

    val prepopulateQuery = "insert into nodes (id, name, link, networkType, isDefault) values " +
        DEFAULT_NODES_LIST.mapIndexed { index, node -> "(${index + 1}, '${node.name}', '${node.link}', ${node.networkType}, ${if (node.isDefault) 1 else 0})" }
            .joinToString()
}