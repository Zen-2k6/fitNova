package uk.ncc.fitNova.workout.history

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.ncc.fitNova.R
import uk.ncc.fitNova.data.prefs.SessionPrefs
import uk.ncc.fitNova.data.remote.BackendConfig
import uk.ncc.fitNova.ui.applyBlackSystemBars
import uk.ncc.fitNova.workout.WorkoutHistorySession
import uk.ncc.fitNova.workout.WorkoutJsonParser
import java.util.Locale

class WorkoutHistoryActivity : AppCompatActivity() {
    private lateinit var backButton: Button
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var historyContainer: LinearLayout
    private val sessionPrefs by lazy { SessionPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_workout_history)
        applyBlackSystemBars(this)
        applySystemBarInsets(findViewById(R.id.main))

        bindViews()
        bindActions()
        loadWorkoutHistory()
    }

    private fun bindViews() {
        backButton = findViewById(R.id.btnBackWorkoutHistory)
        loadingText = findViewById(R.id.tvWorkoutHistoryLoading)
        emptyText = findViewById(R.id.tvWorkoutHistoryEmpty)
        historyContainer = findViewById(R.id.llWorkoutHistoryItems)
    }

    private fun bindActions() {
        backButton.setOnClickListener { finish() }
    }

    private fun applySystemBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(view)
    }

    private fun loadWorkoutHistory() {
        val userId = sessionPrefs.getUserId()
        if (userId <= 0) {
            Toast.makeText(this, R.string.profile_session_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoadingState()

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        showEmptyState()
                        val message = payload.optString("message")
                        if (message.isNotBlank()) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                        return@Listener
                    }

                    val sessions = decodeSessions(payload.optJSONArray("sessions") ?: JSONArray())
                    renderHistory(sessions)
                } catch (_: JSONException) {
                    showEmptyState()
                    Toast.makeText(this, R.string.workout_history_load_failed, Toast.LENGTH_LONG)
                        .show()
                }
            },
            Response.ErrorListener {
                showEmptyState()
                Toast.makeText(this, R.string.workout_history_load_network_error, Toast.LENGTH_LONG)
                    .show()
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "getWorkoutHistory",
                    "userId" to userId.toString(),
                    "limit" to "20"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        return WorkoutJsonParser.decodeSessions(array)
    }

    private fun renderHistory(sessions: List<WorkoutHistorySession>) {
        loadingText.visibility = View.GONE
        historyContainer.removeAllViews()

        if (sessions.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            return
        }

        emptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        sessions.forEach { session ->
            val itemView = inflater.inflate(
                R.layout.item_workout_history,
                historyContainer,
                false
            )

            itemView.findViewById<TextView>(R.id.tvWorkoutHistoryType).text =
                formatWorkoutType(session.workoutType)
            itemView.findViewById<TextView>(R.id.tvWorkoutHistoryDate).text = session.createdAt
            itemView.findViewById<TextView>(R.id.tvWorkoutHistoryDuration).text =
                getString(R.string.workout_history_duration_value, formatDuration(session.durationSeconds))
            val topRightMetric = itemView.findViewById<TextView>(R.id.tvWorkoutHistorySets)
            val bottomLeftMetric = itemView.findViewById<TextView>(R.id.tvWorkoutHistoryReps)
            val bottomRightMetric = itemView.findViewById<TextView>(R.id.tvWorkoutHistoryVolume)
            val setTitle = itemView.findViewById<TextView>(R.id.tvWorkoutHistorySetTitle)
            val logContainer = itemView.findViewById<LinearLayout>(R.id.llWorkoutHistorySetPreview)
            val emptyLogText = itemView.findViewById<TextView>(R.id.tvWorkoutHistorySetEmpty)
            val outdoorStatusText = itemView.findViewById<TextView>(R.id.tvWorkoutHistoryOutdoorStatus)
            val outdoorProgressBar =
                itemView.findViewById<LinearProgressIndicator>(R.id.progressWorkoutHistoryDestination)
            val isOutdoorWorkout = isOutdoorWorkout(session.workoutType)

            logContainer.removeAllViews()

            if (isOutdoorWorkout) {
                topRightMetric.text = getString(
                    R.string.workout_history_distance_value,
                    formatDistance(session.distanceMeters)
                )
                bottomLeftMetric.text = getString(
                    R.string.workout_history_calories_value,
                    formatCalories(session.caloriesBurned)
                )
                bottomRightMetric.text = if (session.destinationLat != null && session.destinationLng != null) {
                    getString(
                        R.string.outdoor_workout_remaining_value,
                        if (session.destinationReached) formatDistance(0.0)
                        else formatDistance(session.remainingDistanceMeters)
                    )
                } else {
                    getString(R.string.outdoor_workout_remaining_default)
                }
                bottomRightMetric.visibility = View.VISIBLE
                outdoorStatusText.visibility = View.VISIBLE
                outdoorProgressBar.visibility = View.VISIBLE
                renderOutdoorProgress(
                    session = session,
                    statusText = outdoorStatusText,
                    progressBar = outdoorProgressBar
                )
                setTitle.visibility = View.GONE
                emptyLogText.visibility = View.GONE
                logContainer.visibility = View.GONE
            } else {
                topRightMetric.text = getString(R.string.workout_history_sets_value, session.totalSets)
                bottomLeftMetric.text = getString(R.string.workout_history_reps_value, session.totalReps)
                bottomRightMetric.text = getString(
                    R.string.workout_history_volume_value,
                    formatWeight(session.totalVolume)
                )
                bottomRightMetric.visibility = View.VISIBLE
                outdoorStatusText.visibility = View.GONE
                outdoorProgressBar.visibility = View.GONE
                setTitle.visibility = View.VISIBLE
                setTitle.text = getString(R.string.workout_history_set_log_title)
                logContainer.visibility = View.VISIBLE

                if (session.setEntries.isEmpty()) {
                    emptyLogText.visibility = View.VISIBLE
                } else {
                    emptyLogText.visibility = View.GONE
                    session.setEntries.take(3).forEachIndexed { index, entry ->
                        val textView = TextView(this).apply {
                            text = getString(
                                R.string.weight_lifting_history_item,
                                index + 1,
                                entry.exercise,
                                formatWeight(entry.weightKg),
                                entry.reps
                            )
                            setTextColor(getColor(R.color.fitness_text_primary))
                            textSize = 14f
                        }
                        logContainer.addView(textView)
                    }

                    if (session.setEntries.size > 3) {
                        val moreText = TextView(this).apply {
                            text = getString(
                                R.string.workout_history_more_sets,
                                session.setEntries.size - 3
                            )
                            setTextColor(getColor(R.color.auth_hint))
                            textSize = 13f
                        }
                        logContainer.addView(moreText)
                    }
                }
            }

            historyContainer.addView(itemView)
        }
    }

    private fun showLoadingState() {
        loadingText.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        historyContainer.removeAllViews()
    }

    private fun showEmptyState() {
        loadingText.visibility = View.GONE
        historyContainer.removeAllViews()
        emptyText.visibility = View.VISIBLE
    }

    private fun formatWorkoutType(rawType: String): String {
        return when (rawType) {
            "weight_lifting" -> getString(R.string.fitness_activity_weight)
            "running" -> getString(R.string.fitness_activity_running)
            "walking" -> getString(R.string.fitness_activity_walking)
            "cycling" -> getString(R.string.fitness_activity_cycling)
            else -> rawType.replace('_', ' ').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatWeight(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        }
    }

    private fun formatCalories(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun renderOutdoorProgress(
        session: WorkoutHistorySession,
        statusText: TextView,
        progressBar: LinearProgressIndicator
    ) {
        if (session.destinationLat == null || session.destinationLng == null) {
            statusText.text = getString(R.string.workout_history_destination_not_set)
            progressBar.visibility = View.GONE
            return
        }

        val progressPercent = calculateDestinationProgressPercent(session)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = progressPercent
        statusText.text = when {
            session.destinationReached -> getString(R.string.workout_history_destination_progress_reached)
            progressPercent > 0 -> getString(
                R.string.workout_history_destination_progress_percent,
                progressPercent
            )
            else -> getString(R.string.workout_history_destination_progress_saved)
        }
    }

    private fun calculateDestinationProgressPercent(session: WorkoutHistorySession): Int {
        if (session.destinationReached) {
            return 100
        }

        val startLat = session.routeStartLat ?: return 0
        val startLng = session.routeStartLng ?: return 0
        val destinationLat = session.destinationLat ?: return 0
        val destinationLng = session.destinationLng ?: return 0

        val initialDistance = calculateDistanceMeters(startLat, startLng, destinationLat, destinationLng)
        if (initialDistance <= 0.0) {
            return 0
        }

        val remaining = session.remainingDistanceMeters.coerceIn(0.0, initialDistance)
        val completedRatio = (initialDistance - remaining) / initialDistance
        return (completedRatio * 100).toInt().coerceIn(0, 100)
    }

    private fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val result = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, result)
        return result[0].toDouble()
    }

    private fun isOutdoorWorkout(workoutType: String): Boolean {
        return workoutType == "walking" || workoutType == "running" || workoutType == "cycling"
    }
}
