package jp.co.soramitsu.common.data.network

object OptionsProvider {
    var CURRENT_VERSION_CODE: Int = 0
    var CURRENT_VERSION_NAME: String = ""
    var APPLICATION_ID: String = ""
    var CURRENT_BUILD_TYPE: String = ""

    val header: String by lazy {
        "$APPLICATION_ID/$CURRENT_VERSION_NAME/$CURRENT_VERSION_CODE/$CURRENT_BUILD_TYPE"
    }
}
