package jp.co.soramitsu.core_db.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences

interface DbDependencies {

    fun gson(): Gson

    fun preferences(): Preferences

    fun context(): Context

    fun secretStoreV1(): SecretStoreV1

    fun secretStoreV2(): SecretStoreV2
}
