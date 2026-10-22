package com.drxwnify.desktop.sync

import kotlinx.coroutines.flow.MutableStateFlow

data class PhoneState(val paired: Boolean = false, val deviceName: String = "", val online: Boolean = false, val lastSync: Long = 0)

/** PC ↔ phone sync over Nostr relays, encrypted to the account's own key. No server of our own. */
object PhoneSync {
    val state = MutableStateFlow(PhoneState())
}
