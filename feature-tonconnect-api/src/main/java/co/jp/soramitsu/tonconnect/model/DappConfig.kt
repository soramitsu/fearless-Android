package co.jp.soramitsu.tonconnect.model

import android.os.Parcelable
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.DappRemote
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
    val icon: String?
) : Parcelable {
    constructor(dappRemote: DappRemote) : this(
        identifier = dappRemote.identifier,
        chains = dappRemote.chains,
        name = dappRemote.name,
        url = dappRemote.url,
        description = dappRemote.description,
        background = dappRemote.background,
        icon = dappRemote.icon
    )

    constructor(tonDappConnection: TonDappConnection) : this(
        identifier = tonDappConnection.clientId,
        chains = listOf("-239"),
        name = tonDappConnection.name,
        url = tonDappConnection.url,
        description = tonDappConnection.url,
        background = null,
        icon = tonDappConnection.icon
    )

}

fun DappConfigRemote.toDomain() = DappConfig(
    type = type,
    apps = apps.map { DappModel(it) }
)