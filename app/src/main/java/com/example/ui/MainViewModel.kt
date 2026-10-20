package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.db.AppDatabase
import com.example.core.data.model.MatchRecord
import com.example.core.data.model.UserSettings
import com.example.core.data.repository.GameRepository
import com.example.core.multiplayer.LocalWifiMultiplayerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = GameRepository(database.matchRecordDao(), database.userSettingsDao())
    val multiplayerManager = LocalWifiMultiplayerManager(application, viewModelScope)

    val settings: StateFlow<UserSettings> = repository.userSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val matchHistory: StateFlow<List<MatchRecord>> = repository.matchHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalMatchesCount: StateFlow<Int> = repository.totalMatchesCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    init {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                multiplayerManager.customUsername = currentSettings.username
            }
        }
    }

    fun setUsername(newUsername: String): kotlinx.coroutines.Job? {
        val trimmed = newUsername.trim()
        if (trimmed.isNotEmpty()) {
            multiplayerManager.customUsername = trimmed
            return viewModelScope.launch {
                repository.updateUsername(trimmed)
            }
        }
        return null
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(soundEnabled = enabled))
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(vibrationEnabled = enabled))
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(themeMode = mode))
        }
    }

    fun clearStats() {
        viewModelScope.launch {
            repository.clearAllStats()
        }
    }

    override fun onCleared() {
        super.onCleared()
        multiplayerManager.disconnect()
    }
}
