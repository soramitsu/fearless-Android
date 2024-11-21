package co.jp.soramitsu.tonconnect.model

import android.os.Parcelable
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.DappRemote
import jp.co.soramitsu.coredb.model.TonConnectionLocal
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
): Parcelable

fun DappConfigRemote.toDomain() = DappConfig(
    type = type,
    apps = apps.map { it.toDomain() }
)

fun DappRemote.toDomain() = DappModel(
    identifier = identifier,
    chains = chains,
    name = name,
    url = url,
    description = description,
    background = background,
    icon = icon
)

fun TonConnectionLocal.toDomain() = DappModel (
    identifier = clientId,
    chains = listOf("-239"),
    name = name,
    url = url,
    description = null,
    background = null,
    icon = icon
)