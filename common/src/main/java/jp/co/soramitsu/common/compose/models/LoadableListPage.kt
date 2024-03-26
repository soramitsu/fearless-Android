package jp.co.soramitsu.common.compose.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
sealed interface LoadableListPage<T> {
    @Stable
    interface PreviousPageLoading<T>: LoadableListPage<T>

    @Stable
    interface NextPageLoading<T>: LoadableListPage<T>

    @Stable
    interface Reloading<T>: LoadableListPage<T> {
        val views: Collection<T>
    }

    /*
        While other members of LoadableListPage can be state objects,
         and their recreation can be omitted, this specific case MUST be recreated on each new entry
     */
    @Immutable
    interface ReadyToRender<T>: LoadableListPage<T> {
        val views: Collection<T>
    }
}