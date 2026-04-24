package com.cory.noter

import android.app.Application
import com.cory.noter.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoterApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        reconcileStartupState()
    }

    fun reconcileStartupState(onComplete: (() -> Unit)? = null) {
        applicationScope.launch {
            try {
                appContainer.startupReconciliation.reconcile()
            } finally {
                onComplete?.invoke()
            }
        }
    }
}
