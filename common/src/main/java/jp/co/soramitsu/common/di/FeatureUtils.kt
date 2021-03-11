package jp.co.soramitsu.common.di

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment

object FeatureUtils {

    fun <T> getFeature(context: Context, key: Class<*>): T {
        return getHolder(context).getFeature(key)
    }

    fun <T> getFeature(activity: Activity, key: Class<*>): T {
        return getHolder(activity.applicationContext).getFeature(key)
    }

    fun <T> getFeature(fragment: Fragment, key: Class<*>): T {
        return getHolder(fragment.context!!).getFeature(key)
    }

    fun releaseFeature(context: Context, key: Class<*>) {
        getHolder(context).releaseFeature(key)
    }

    fun releaseFeature(context: Activity, key: Class<*>) {
        getHolder(context.applicationContext).releaseFeature(key)
    }

    fun releaseFeature(fragment: Fragment, key: Class<*>) {
        getHolder(fragment.context!!).releaseFeature(key)
    }

    private fun getHolder(context: Context): FeatureContainer {
        return context.applicationContext as FeatureContainer
    }
}
