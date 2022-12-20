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

-keep class jp.co.soramitsu.crowdloan.api.data.** { *; }

-keep class jp.co.soramitsu.crowdloan.impl.data.** { *; }
-keep class jp.co.soramitsu.crowdloan.impl.domain.** { *; }

-keep class jp.co.soramitsu.core_db.** { *; }
-keep class jp.co.soramitsu.coredb.** { *; }
-keep class jp.co.soramitsu.core.** { *; }

-keep class net.jpountz.** { *; }

#for beacon sdk

-keep class it.airgap.beaconsdk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class it.airgap.beaconsdk.**$$serializer { *; }
-keepclassmembers class it.airgap.beaconsdk.** {
    *** Companion;
}
-keepclasseswithmembers class it.airgap.beaconsdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn java.awt.*