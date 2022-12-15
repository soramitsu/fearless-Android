package jp.co.soramitsu.crowdloan.impl.domain.common

import java.math.BigInteger

fun leaseIndexFromBlock(block: BigInteger, blocksPerLeasePeriod: BigInteger, leaseOffset: BigInteger) = (block - leaseOffset) / blocksPerLeasePeriod
