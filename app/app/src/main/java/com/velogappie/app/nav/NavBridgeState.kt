package com.velogappie.app.nav

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.velogappie.app.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NavBridgeState {
    var isEnabled: Boolean = false
        set(value) {
            field = value
            _context?.let { AppSettings.setNavBridgeEnabled(it, value) }
        }

    private var _context: Context? = null

    fun init(context: Context) {
        _context = context.applicationContext
        isEnabled = AppSettings.isNavBridgeEnabled(context)
    }

    private val _instruction = MutableStateFlow<NavInstruction?>(null)
    val instruction: StateFlow<NavInstruction?> = _instruction

    var latestInstruction: NavInstruction?
        get() = _instruction.value
        set(value) { _instruction.value = value }

    private val _currentTrack = MutableStateFlow<String?>(null)
    val currentTrack: StateFlow<String?> = _currentTrack

    var latestTrack: String?
        get() = _currentTrack.value
        set(value) { _currentTrack.value = value }

    fun hasNotificationAccess(context: Context): Boolean {
        val cn = ComponentName(context, NavBridgeService::class.java).flattenToString()
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(cn) == true
    }
}
