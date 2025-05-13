package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema


interface SecretStore<T: Schema<T>> {
    fun put(metaId: Long, secrets: EncodableStruct<T>)
    fun get(metaId: Long): EncodableStruct<T>?
}



