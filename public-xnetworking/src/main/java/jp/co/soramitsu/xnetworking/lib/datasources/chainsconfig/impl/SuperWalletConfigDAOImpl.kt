package jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.data.ConfigParser

class SuperWalletConfigDAOImpl(
    val configParser: ConfigParser
) : ConfigDAO
