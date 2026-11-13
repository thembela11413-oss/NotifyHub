package com.notifyhub.ui

/** Pure function so it can be unit tested. */
fun collectorStatus(accessGranted: Boolean, paused: Boolean, serviceConnected: Boolean): String = when {
    !accessGranted -> "Notification access needed"
    paused -> "Paused"
    serviceConnected -> "Running"
    else -> "Starting…"
}
