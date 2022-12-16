## General
#-keep class ** { *; }
#-dontobfuscate
-keep class jp.co.soramitsu.fearless_utils.** { *; }
-keep class jp.co.soramitsu.runtime.** { *; }

-keep class jp.co.soramitsu.wallet.impl.data.** { *; }
-keep class jp.co.soramitsu.wallet.impl.domain.** { *; }

-keep class jp.co.soramitsu.wallet.api.data.** { *; }
-keep class jp.co.soramitsu.wallet.api.domain.** { *; }

-keep class jp.co.soramitsu.staking.impl.domain.** { *; }
-keep class jp.co.soramitsu.staking.impl.data.** { *; }

-keep class jp.co.soramitsu.staking.api.domain.** { *; }
-keep class jp.co.soramitsu.staking.api.data.** { *; }

-keep class jp.co.soramitsu.account.api.data.** { *; }
-keep class jp.co.soramitsu.account.api.domain.** { *; }

-keep class jp.co.soramitsu.account.impl.data.** { *; }
-keep class jp.co.soramitsu.account.impl.domain.** { *; }

-keep class jp.co.soramitsu.common.data.** { *; }
-keep class jp.co.soramitsu.common.domain.** { *; }

-keep class jp.co.soramitsu.core_db.** { *; }
-keep class jp.co.soramitsu.coredb.** { *; }
-keep class jp.co.soramitsu.core.** { *; }

-keep class net.jpountz.** { *; }