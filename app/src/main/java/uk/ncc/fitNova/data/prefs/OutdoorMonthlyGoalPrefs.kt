package uk.ncc.fitNova.data.prefs

import android.content.Context

data class OutdoorMonthlyGoalSnapshot(
    val distanceGoalKm: Double = 0.0,
    val caloriesGoalKcal: Double = 0.0
)

class OutdoorMonthlyGoalPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(userId: Int, workoutType: String, monthKey: String): OutdoorMonthlyGoalSnapshot {
        return OutdoorMonthlyGoalSnapshot(
            distanceGoalKm = prefs.getFloat(buildKey(userId, workoutType, monthKey, KEY_DISTANCE), 0f)
                .toDouble(),
            caloriesGoalKcal = prefs.getFloat(buildKey(userId, workoutType, monthKey, KEY_CALORIES), 0f)
                .toDouble()
        )
    }

    fun save(
        userId: Int,
        workoutType: String,
        monthKey: String,
        snapshot: OutdoorMonthlyGoalSnapshot
    ) {
        prefs.edit()
            .putFloat(
                buildKey(userId, workoutType, monthKey, KEY_DISTANCE),
                snapshot.distanceGoalKm.toFloat()
            )
            .putFloat(
                buildKey(userId, workoutType, monthKey, KEY_CALORIES),
                snapshot.caloriesGoalKcal.toFloat()
            )
            .apply()
    }

    private fun buildKey(userId: Int, workoutType: String, monthKey: String, suffix: String): String {
        return "${userId}_${workoutType}_${monthKey}_$suffix"
    }

    companion object {
        private const val PREFS_NAME = "OutdoorMonthlyGoalPrefs"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_CALORIES = "calories"
    }
}
