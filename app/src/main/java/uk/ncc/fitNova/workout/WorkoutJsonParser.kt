package uk.ncc.fitNova.workout

import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object WorkoutJsonParser {
    fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        val sessions = mutableListOf<WorkoutHistorySession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            sessions.add(decodeSession(item))
        }
        return sessions
    }

    fun decodeSetEntries(rawJson: String): List<WeightLiftingSetEntry> {
        if (rawJson.isBlank()) {
            return emptyList()
        }

        return try {
            val jsonArray = JSONArray(rawJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    add(
                        WeightLiftingSetEntry(
                            exercise = item.optString("exercise"),
                            weightKg = item.optDouble("weightKg"),
                            reps = item.optInt("reps")
                        )
                    )
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun decodeRouteSummary(rawJson: String): RouteSummary {
        if (rawJson.isBlank()) {
            return RouteSummary()
        }

        return try {
            val array = JSONArray(rawJson)
            val firstPoint = array.optJSONObject(0)?.let { point ->
                LatLng(point.optDouble("lat"), point.optDouble("lng"))
            }
            RouteSummary(
                pointCount = array.length(),
                firstPoint = firstPoint
            )
        } catch (_: JSONException) {
            RouteSummary()
        }
    }

    fun parseNullableDouble(item: JSONObject, key: String): Double? {
        if (item.isNull(key)) {
            return null
        }
        return item.optString(key).toDoubleOrNull()
    }

    private fun decodeSession(item: JSONObject): WorkoutHistorySession {
        val routeSummary = decodeRouteSummary(item.optString("routeJson"))
        return WorkoutHistorySession(
            id = item.optString("id"),
            workoutType = item.optString("workoutType"),
            durationSeconds = item.optInt("durationSeconds"),
            totalSets = item.optInt("totalSets"),
            totalReps = item.optInt("totalReps"),
            totalVolume = item.optDouble("totalVolume"),
            distanceMeters = item.optDouble("distanceMeters"),
            caloriesBurned = item.optDouble("caloriesBurned"),
            routeName = item.optString("routeName"),
            destinationLat = parseNullableDouble(item, "destinationLat"),
            destinationLng = parseNullableDouble(item, "destinationLng"),
            remainingDistanceMeters = item.optDouble("remainingDistanceMeters"),
            destinationReached = item.optString("destinationReached") == "1",
            routePointCount = routeSummary.pointCount,
            routeStartLat = routeSummary.firstPoint?.latitude,
            routeStartLng = routeSummary.firstPoint?.longitude,
            targetDurationSeconds = item.optInt("targetDurationSeconds"),
            targetDurationReached = item.optString("targetDurationReached") == "1",
            distanceGoalMeters = item.optDouble("distanceGoalMeters"),
            caloriesGoal = item.optDouble("caloriesGoal"),
            createdAt = item.optString("createdAt"),
            setEntries = decodeSetEntries(item.optString("setLogJson"))
        )
    }
}
