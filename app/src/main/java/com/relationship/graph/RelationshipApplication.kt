package com.relationship.graph

import android.app.Application
import com.google.gson.Gson
import com.relationship.graph.data.RelationshipRepository
import com.relationship.graph.data.backup.BackupManager
import com.relationship.graph.data.local.AppDatabase
import com.relationship.graph.data.security.SecretStore
import com.relationship.graph.data.security.SecurityStore
import com.relationship.graph.security.AppLockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RelationshipApplication : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var appLockController: AppLockController
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val securityStore = SecurityStore(this)
        container = AppContainer(
            repository = RelationshipRepository(
                context = this,
                database = AppDatabase.create(this, SecretStore(this)),
            ),
            backupManager = null,
            gson = Gson(),
        )
        container = container.copy(
            backupManager = BackupManager(
                context = this,
                repository = container.repository,
                gson = container.gson,
            ),
        )
        appLockController = AppLockController(this, securityStore, appScope)
    }
}

data class AppContainer(
    val repository: RelationshipRepository,
    val backupManager: BackupManager?,
    val gson: Gson,
)
