package jp.co.soramitsu.feature_wallet_api.domain.validation

import java.math.BigDecimal

typealias AmountProducer<P> = suspend (P) -> BigDecimal

typealias TokenProducer<P> = suspend (P) -> Token
