package com.codex.edgeshelf.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object EdgeDistancePreview {
    private val mutableDistanceDp = MutableStateFlow<Float?>(null)

    val distanceDp: StateFlow<Float?> = mutableDistanceDp.asStateFlow()

    fun update(distanceDp: Float) {
        mutableDistanceDp.value = normalizeEdgeDistanceDp(distanceDp)
    }

    fun clear(expectedDistanceDp: Float? = null) {
        val expected = expectedDistanceDp?.let(::normalizeEdgeDistanceDp)
        if (expected == null || mutableDistanceDp.value == expected) {
            mutableDistanceDp.value = null
        }
    }
}
