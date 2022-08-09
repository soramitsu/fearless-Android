package jp.co.soramitsu.featurewalletapi.domain.validation

import jp.co.soramitsu.featurewalletapi.domain.model.Token
import java.math.BigDecimal

typealias AmountProducer<P> = suspend (P) -> BigDecimal

typealias TokenProducer<P> = suspend (P) -> Token
