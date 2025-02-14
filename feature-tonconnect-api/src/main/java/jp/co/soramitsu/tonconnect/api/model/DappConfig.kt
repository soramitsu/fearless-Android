package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcelable
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.DappRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import kotlinx.parcelize.Parcelize

data class DappConfig(
    val type: String?,
    val apps: List<DappModel>,
)

@Parcelize
data class DappModel(
    val identifier: String,
    val chains: List<String>,
    val name: String?,
    val url: String?,
    val description: String?,
    val background: String?,
    val icon: String?,
    val metaId: Long?
) : Parcelable {
    constructor(dappRemote: DappRemote) : this(
        identifier = dappRemote.identifier,
        chains = dappRemote.chains,
        name = dappRemote.name,
        url = dappRemote.url,
        description = dappRemote.description,
        background = dappRemote.background,
        icon = dappRemote.icon,
        metaId = null
    )

    constructor(tonDappConnection: TonDappConnection) : this(
        identifier = tonDappConnection.clientId,
        chains = listOf(tonMainnetChainId),
        name = tonDappConnection.name,
        url = tonDappConnection.url,
        description = tonDappConnection.url,
        background = null,
        icon = tonDappConnection.icon,
        metaId = tonDappConnection.metaId
    )
}

fun DappConfigRemote.toDomain() = DappConfig(
    type = type,
    apps = apps.map { DappModel(it) }
)
