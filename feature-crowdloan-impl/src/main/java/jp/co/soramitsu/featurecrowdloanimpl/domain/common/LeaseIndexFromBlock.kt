package jp.co.soramitsu.featurecrowdloanimpl.domain.common

import java.math.BigInteger

fun leaseIndexFromBlock(block: BigInteger, blocksPerLeasePeriod: BigInteger) = block / blocksPerLeasePeriod
