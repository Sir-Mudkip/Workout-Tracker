package com.luke.workouttracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BodyweightPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bodyweight_prefs", Context.MODE_PRIVATE)

    private val _kg = MutableStateFlow(read())
    val kg: StateFlow<Double> = _kg.asStateFlow()

    fun set(value: Double) {
        prefs.edit().putFloat(KEY, value.toFloat()).apply()
        _kg.value = value
    }

    private fun read(): Double {
        return if (prefs.contains(KEY)) prefs.getFloat(KEY, DEFAULT.toFloat()).toDouble() else DEFAULT
    }

    companion object {
        const val DEFAULT = 75.0
        private const val KEY = "kg"
    }
}
