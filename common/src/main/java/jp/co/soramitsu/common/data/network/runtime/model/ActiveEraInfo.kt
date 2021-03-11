package jp.co.soramitsu.common.data.network.runtime.model

import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.uint32

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}
