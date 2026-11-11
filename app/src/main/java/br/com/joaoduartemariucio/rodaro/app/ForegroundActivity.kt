package br.com.joaoduartemariucio.rodaro.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object ForegroundActivity : Application.ActivityLifecycleCallbacks {
    private var reference: WeakReference<Activity> = WeakReference(null)

    val current: Activity?
        get() = reference.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        reference = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        reference = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (reference.get() === activity) reference = WeakReference(null)
    }
}
