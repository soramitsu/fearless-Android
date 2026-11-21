package jp.co.soramitsu.onboarding.impl.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer

/**
 * Renders a private top-level composable (typically a @Preview) of [fileClass] (the Kotlin file class, for example
 * "jp.co.soramitsu.x.FooScreenKt") inside the current composition. The Compose compiler turns a parameterless
 * composable into a static method taking (Composer, Int changed); any further Int parameters are default masks.
 */
@Composable
fun PrivateComposable(fileClass: String, name: String) {
    val method = Class.forName(fileClass).declaredMethods.single { candidate ->
        candidate.name == name && candidate.parameterTypes.firstOrNull() == Composer::class.java
    }
    method.isAccessible = true
    val args = arrayOfNulls<Any>(method.parameterCount)
    args[0] = currentComposer
    for (index in 1 until args.size) args[index] = 0
    method.invoke(null, *args)
}
