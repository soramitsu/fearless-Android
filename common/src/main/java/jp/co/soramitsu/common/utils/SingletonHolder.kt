package jp.co.soramitsu.common.utils

open class SingletonHolder<out T, in A, in B>(creator: (A, B) -> T) {

    private var creator: ((A, B) -> T)? = creator

    @Volatile private var instance: T? = null

    fun getInstanceOrInit(arg1: A, arg2: B): T {
        val localInstance = instance
        if (localInstance != null) {
            return localInstance
        }

        return synchronized(this) {
            val i = instance
            if (i != null) {
                i
            } else {
                val created = creator!!(arg1, arg2)
                instance = created
                creator = null
                created
            }
        }
    }

    fun getInstance(): T? {
        return instance
    }
}