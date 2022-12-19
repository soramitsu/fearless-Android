package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal

typealias AmountProducer<P> = suspend (P) -> BigDecimal

typealias TokenProducer<P> = suspend (P) -> Token
