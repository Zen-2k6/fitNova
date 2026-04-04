package uk.ncc.fitNova.workout.common

import android.content.Context
import android.content.Intent
import uk.ncc.fitNova.workout.outdoor.OutdoorWorkoutActivity
import uk.ncc.fitNova.workout.strength.WeightLiftingActivity

object WorkoutNavigation {
    const val TYPE_RUNNING = "running"
    const val TYPE_WALKING = "walking"
    const val TYPE_WEIGHT_LIFTING = "weight_lifting"
    const val TYPE_CYCLING = "cycling"

    private const val EXTRA_WORKOUT_TYPE = "WORKOUT_TYPE"

    fun createIntent(context: Context, workoutType: String): Intent {
        return when (workoutType) {
            TYPE_WEIGHT_LIFTING -> Intent(context, WeightLiftingActivity::class.java)
            TYPE_RUNNING, TYPE_WALKING, TYPE_CYCLING -> {
                Intent(context, OutdoorWorkoutActivity::class.java).apply {
                    putExtra(EXTRA_WORKOUT_TYPE, workoutType)
                }
            }
            else -> Intent(context, OutdoorWorkoutActivity::class.java).apply {
                putExtra(EXTRA_WORKOUT_TYPE, TYPE_WALKING)
            }
        }
    }
}
