package uk.ncc.fitNova.workout

import com.google.android.gms.maps.model.LatLng

data class WeightLiftingSetEntry(
    val exercise: String,
    val weightKg: Double,
    val reps: Int
)

data class WorkoutHistorySession(
    val id: String,
    val workoutType: String,
    val durationSeconds: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolume: Double,
    val distanceMeters: Double,
    val caloriesBurned: Double,
    val routeName: String = "",
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val remainingDistanceMeters: Double = 0.0,
    val destinationReached: Boolean = false,
    val routePointCount: Int = 0,
    val routeStartLat: Double? = null,
    val routeStartLng: Double? = null,
    val targetDurationSeconds: Int = 0,
    val targetDurationReached: Boolean = false,
    val distanceGoalMeters: Double = 0.0,
    val caloriesGoal: Double = 0.0,
    val createdAt: String,
    val setEntries: List<WeightLiftingSetEntry>
)

data class RouteSummary(
    val pointCount: Int = 0,
    val firstPoint: LatLng? = null
)
