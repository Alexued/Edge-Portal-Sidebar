package com.codex.edgeshelf.permissions

data class PermissionSnapshot(
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val usageAccessGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
)
