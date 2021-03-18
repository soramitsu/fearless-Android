package jp.co.soramitsu.common.data.network.subscan

import jp.co.soramitsu.core.model.Node
import java.util.Locale

fun Node.NetworkType.subscanSubDomain() = readableName.toLowerCase(Locale.ROOT)
