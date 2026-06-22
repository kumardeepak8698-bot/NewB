package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.CloneManager
import com.example.DeviceRandomizer
import com.example.data.ClonerDatabase
import com.example.models.AppInfo
import com.example.models.ClonedApp
import com.example.models.SpoofProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClonerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ClonerDatabase.getDatabase(application)
    private val dao = db.clonerDao()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    val profiles: StateFlow<List<SpoofProfile>> = dao.getAllProfilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clonedApps: StateFlow<List<ClonedApp>> = dao.getAllClonedAppsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cloningState = MutableStateFlow<CloningState>(CloningState.Idle)
    val cloningState: StateFlow<CloningState> = _cloningState.asStateFlow()

    private val _cloningLogs = MutableStateFlow<List<String>>(emptyList())
    val cloningLogs: StateFlow<List<String>> = _cloningLogs.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp: StateFlow<AppInfo?> = _selectedApp.asStateFlow()

    private val _runningSandboxApp = MutableStateFlow<SandboxRunningState?>(null)
    val runningSandboxApp: StateFlow<SandboxRunningState?> = _runningSandboxApp.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
        seedDefaultProfiles()
    }

    private fun seedDefaultProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = dao.getAllProfilesFlow().first().size
            if (count == 0) {
                dao.insertProfile(DeviceRandomizer.generateRandomProfile("Default Security Identity"))
                dao.insertProfile(DeviceRandomizer.generateRandomProfile("Stealth Profile One"))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectAppForClone(app: AppInfo?) {
        _selectedApp.value = app
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apps = CloneManager.getInstalledApps(getApplication())
                _installedApps.value = apps
            } catch (e: Exception) {
                _installedApps.value = emptyList()
            }
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getProfileById(id)?.let {
                dao.deleteProfile(it)
            }
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val randomProfile = DeviceRandomizer.generateRandomProfile(name)
            dao.insertProfile(randomProfile)
        }
    }

    fun deleteClonedApp(clonedApp: ClonedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteClonedApp(clonedApp)
        }
    }

    fun runCloneSimulation(app: AppInfo, profileId: Long) {
        viewModelScope.launch {
            _cloningState.value = CloningState.Running
            _cloningLogs.value = emptyList()

            val targetPkg = "${app.packageName}.clone"

            val success = CloneManager.cloneApp(
                context = getApplication(),
                app = app,
                targetPackageName = targetPkg,
                profileId = profileId,
                dao = dao,
                onProgress = { logLine ->
                    _cloningLogs.value = _cloningLogs.value + logLine
                }
            )

            if (success) {
                _cloningState.value = CloningState.Success(app)
            } else {
                _cloningState.value = CloningState.Error("Signing/Installation container build failed.")
            }
        }
    }

    fun clearCloningProgress() {
        _cloningState.value = CloningState.Idle
        _cloningLogs.value = emptyList()
    }

    fun launchVirtualApp(clonedApp: ClonedApp) {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) {
                dao.getProfileById(clonedApp.spoofProfileId)
            } ?: DeviceRandomizer.generateRandomProfile("Dynamic Profile")

            _runningSandboxApp.value = SandboxRunningState(
                clonedApp = clonedApp,
                deviceProfile = profile,
                isRunning = true,
                uptimeSec = 0
            )
        }
    }

    fun stopVirtualApp() {
        _runningSandboxApp.value = null
    }

    class Factory(private val valApplication: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ClonerViewModel(valApplication) as T
        }
    }
}

sealed class CloningState {
    object Idle : CloningState()
    object Running : CloningState()
    data class Success(val app: AppInfo) : CloningState()
    data class Error(val message: String) : CloningState()
}

data class SandboxRunningState(
    val clonedApp: ClonedApp,
    val deviceProfile: SpoofProfile,
    val isRunning: Boolean,
    val uptimeSec: Int
)
