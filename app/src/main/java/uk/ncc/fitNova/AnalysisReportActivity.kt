package uk.ncc.fitNova

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

class AnalysisReportActivity : AppCompatActivity() {
    private lateinit var backButton: Button
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var totalsCard: View
    private lateinit var trendsCard: View
    private lateinit var bodyCard: View
    private lateinit var summaryCard: View
    private lateinit var totalSessionsValue: TextView
    private lateinit var totalTimeValue: TextView
    private lateinit var totalDistanceValue: TextView
    private lateinit var totalCaloriesValue: TextView
    private lateinit var bestActivityValue: TextView
    private lateinit var averageSessionValue: TextView
    private lateinit var longestSessionValue: TextView
    private lateinit var last7DaysValue: TextView
    private lateinit var weeklyTrendEmptyText: TextView
    private lateinit var weeklyTrendChartContainer: LinearLayout
    private lateinit var bmiValue: TextView
    private lateinit var waterGoalValue: TextView
    private lateinit var healthyRangeValue: TextView
    private lateinit var summaryBody: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analysis_report)
        applySystemBarInsets(findViewById(R.id.main))

        bindViews()
        bindActions()
        loadReport()
    }

    private fun bindViews() {
        backButton = findViewById(R.id.btnBackAnalysis)
        loadingText = findViewById(R.id.tvAnalysisLoading)
        emptyText = findViewById(R.id.tvAnalysisEmpty)
        totalsCard = findViewById(R.id.cardAnalysisTotals)
        trendsCard = findViewById(R.id.cardAnalysisTrends)
        bodyCard = findViewById(R.id.cardAnalysisBody)
        summaryCard = findViewById(R.id.cardAnalysisSummary)
        totalSessionsValue = findViewById(R.id.tvAnalysisTotalSessionsValue)
        totalTimeValue = findViewById(R.id.tvAnalysisTotalTimeValue)
        totalDistanceValue = findViewById(R.id.tvAnalysisDistanceValue)
        totalCaloriesValue = findViewById(R.id.tvAnalysisCaloriesValue)
        bestActivityValue = findViewById(R.id.tvAnalysisBestActivityValue)
        averageSessionValue = findViewById(R.id.tvAnalysisAverageSessionValue)
        longestSessionValue = findViewById(R.id.tvAnalysisLongestSessionValue)
        last7DaysValue = findViewById(R.id.tvAnalysisLast7DaysValue)
        weeklyTrendEmptyText = findViewById(R.id.tvAnalysisWeeklyTrendEmpty)
        weeklyTrendChartContainer = findViewById(R.id.llAnalysisWeeklyTrendChart)
        bmiValue = findViewById(R.id.tvAnalysisBmiValue)
        waterGoalValue = findViewById(R.id.tvAnalysisWaterGoalValue)
        healthyRangeValue = findViewById(R.id.tvAnalysisHealthyRangeValue)
        summaryBody = findViewById(R.id.tvAnalysisSummaryBody)
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

    private fun loadReport() {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("User_id", -1)
        if (userId <= 0) {
            Toast.makeText(this, R.string.profile_session_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoadingState()
        renderBodySignals(sharedPref)

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        showErrorState(payload.optString("message"))
                        return@Listener
                    }

                    val sessions = decodeSessions(payload.optJSONArray("sessions") ?: JSONArray())
                    renderReport(sessions, sharedPref)
                } catch (_: JSONException) {
                    showErrorState(getString(R.string.workout_history_load_failed))
                }
            },
            Response.ErrorListener {
                showErrorState(getString(R.string.workout_history_load_network_error))
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "getWorkoutHistory",
                    "userId" to userId.toString(),
                    "limit" to "50"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        val sessions = mutableListOf<WorkoutHistorySession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val setEntries = decodeSetEntries(item.optString("setLogJson"))
            sessions.add(
                WorkoutHistorySession(
                    id = item.optString("id"),
                    workoutType = item.optString("workoutType"),
                    durationSeconds = item.optInt("durationSeconds"),
                    totalSets = item.optInt("totalSets"),
                    totalReps = item.optInt("totalReps"),
                    totalVolume = item.optDouble("totalVolume"),
                    distanceMeters = item.optDouble("distanceMeters"),
                    caloriesBurned = item.optDouble("caloriesBurned"),
                    createdAt = item.optString("createdAt"),
                    setEntries = setEntries
                )
            )
        }
        return sessions
    }

    private fun decodeSetEntries(rawJson: String): List<WeightLiftingSetEntry> {
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

    private fun renderReport(
        sessions: List<WorkoutHistorySession>,
        sharedPref: android.content.SharedPreferences
    ) {
        val totalSessions = sessions.size
        val totalDurationSeconds = sessions.sumOf { it.durationSeconds }
        val totalDistanceMeters = sessions.sumOf { it.distanceMeters }
        val totalCalories = sessions.sumOf { it.caloriesBurned }
        val averageDurationSeconds = if (totalSessions > 0) {
            totalDurationSeconds / totalSessions
        } else {
            0
        }

        val favoriteType = sessions.groupingBy { it.workoutType }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val longestSession = sessions.maxByOrNull { it.durationSeconds }
        val recentSessions = sessions.filter { isWithinLastDays(it.createdAt, 7) }
        val recentDurationSeconds = recentSessions.sumOf { it.durationSeconds }

        loadingText.visibility = View.GONE
        emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        totalsCard.visibility = View.VISIBLE
        trendsCard.visibility = View.VISIBLE
        bodyCard.visibility = View.VISIBLE
        summaryCard.visibility = View.VISIBLE

        totalSessionsValue.text = totalSessions.toString()
        totalTimeValue.text = formatDuration(totalDurationSeconds)
        totalDistanceValue.text = formatDistance(totalDistanceMeters)
        totalCaloriesValue.text = formatCalories(totalCalories)

        bestActivityValue.text = buildLabelLine(
            R.string.analysis_report_best_activity,
            favoriteType?.let { formatWorkoutType(it) } ?: getString(R.string.analysis_report_unknown)
        )
        averageSessionValue.text = buildLabelLine(
            R.string.analysis_report_average_session,
            formatDuration(averageDurationSeconds)
        )
        longestSessionValue.text = buildLabelLine(
            R.string.analysis_report_longest_session,
            if (longestSession == null) {
                getString(R.string.analysis_report_unknown)
            } else {
                "${formatWorkoutType(longestSession.workoutType)} • ${formatDuration(longestSession.durationSeconds)}"
            }
        )
        last7DaysValue.text = buildLabelLine(
            R.string.analysis_report_last_7_days,
            if (recentSessions.isEmpty()) {
                getString(R.string.analysis_report_sessions_value, 0)
            } else {
                "${recentSessions.size} sessions • ${formatDuration(recentDurationSeconds)}"
            }
        )
        renderWeeklyTrendChart(sessions)

        renderBodySignals(sharedPref)
        summaryBody.text = buildSummaryText(
            totalSessions = totalSessions,
            favoriteType = favoriteType,
            totalDistanceMeters = totalDistanceMeters,
            totalCalories = totalCalories,
            bmiLine = bmiValue.text.toString()
        )
    }

    private fun renderBodySignals(sharedPref: android.content.SharedPreferences) {
        val weight = sharedPref.getInt("Weight", 0)
        val height = sharedPref.getInt("Height", 0)

        if (weight <= 0 || height <= 0) {
            bmiValue.text = buildLabelLine(
                R.string.analysis_report_body_bmi,
                getString(R.string.analysis_report_unknown)
            )
            waterGoalValue.text = buildLabelLine(
                R.string.analysis_report_body_water,
                getString(R.string.analysis_report_unknown)
            )
            healthyRangeValue.text = buildLabelLine(
                R.string.analysis_report_body_range,
                getString(R.string.analysis_report_unknown)
            )
            return
        }

        val bmi = calculateBmi(weight, height)
        val bmiLabel = when {
            bmi < 18.5 -> getString(R.string.fitness_bmi_underweight)
            bmi < 25.0 -> getString(R.string.fitness_bmi_healthy)
            bmi < 30.0 -> getString(R.string.fitness_bmi_overweight)
            else -> getString(R.string.fitness_bmi_obesity)
        }
        val waterGoalLiters = weight * 0.035
        val healthyRange = calculateHealthyWeightRange(height)

        bmiValue.text = buildLabelLine(
            R.string.analysis_report_body_bmi,
            "${String.format(Locale.getDefault(), "%.1f", bmi)} ($bmiLabel)"
        )
        waterGoalValue.text = buildLabelLine(
            R.string.analysis_report_body_water,
            getString(R.string.fitness_water_goal_value, waterGoalLiters)
        )
        healthyRangeValue.text = buildLabelLine(
            R.string.analysis_report_body_range,
            getString(
                R.string.fitness_weight_range_value,
                healthyRange.first,
                healthyRange.second
            )
        )
    }

    private fun buildSummaryText(
        totalSessions: Int,
        favoriteType: String?,
        totalDistanceMeters: Double,
        totalCalories: Double,
        bmiLine: String
    ): String {
        if (totalSessions == 0) {
            return "No saved sessions yet. Start with walking, running, cycling, or weight lifting and this screen will turn into a real training report."
        }

        val topActivityText = favoriteType?.let { formatWorkoutType(it) } ?: "training"
        val distanceText = formatDistance(totalDistanceMeters)
        val caloriesText = formatCalories(totalCalories)

        return "You have logged $totalSessions sessions so far, with $topActivityText showing up most often. Across those workouts you have covered $distanceText and burned about $caloriesText. $bmiLine, so use that together with your recent training load when planning the next week."
    }

    private fun buildLabelLine(labelRes: Int, value: String): String {
        return "${getString(labelRes)}: $value"
    }

    private fun renderWeeklyTrendChart(sessions: List<WorkoutHistorySession>) {
        weeklyTrendChartContainer.removeAllViews()

        val today = LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val totalsByDay = days.associateWith { TrendDayTotals() }.toMutableMap()

        sessions.forEach { session ->
            if (!isOutdoorTrendType(session.workoutType)) {
                return@forEach
            }

            val sessionDate = parseSessionDate(session.createdAt) ?: return@forEach
            val dayTotals = totalsByDay[sessionDate] ?: return@forEach

            when (session.workoutType) {
                "running" -> dayTotals.runningSeconds += session.durationSeconds
                "walking" -> dayTotals.walkingSeconds += session.durationSeconds
                "cycling" -> dayTotals.cyclingSeconds += session.durationSeconds
            }
        }

        val maxSeconds = totalsByDay.values
            .flatMap { listOf(it.runningSeconds, it.walkingSeconds, it.cyclingSeconds) }
            .maxOrNull()
            ?: 0

        weeklyTrendEmptyText.visibility = if (maxSeconds == 0) View.VISIBLE else View.GONE
        weeklyTrendChartContainer.visibility = if (maxSeconds == 0) View.GONE else View.VISIBLE

        if (maxSeconds == 0) {
            return
        }

        val inflater = LayoutInflater.from(this)
        days.forEachIndexed { index, day ->
            val itemView = inflater.inflate(
                R.layout.item_analysis_weekly_trend_day,
                weeklyTrendChartContainer,
                false
            )
            val totals = totalsByDay[day] ?: TrendDayTotals()
            val totalSecondsForDay =
                totals.runningSeconds + totals.walkingSeconds + totals.cyclingSeconds
            val totalMinutesForDay = if (totalSecondsForDay > 0) {
                ((totalSecondsForDay + 59) / 60)
            } else {
                0
            }

            itemView.findViewById<TextView>(R.id.tvTrendValueLabel).text = getString(
                R.string.analysis_report_weekly_trend_value,
                totalMinutesForDay
            )
            itemView.findViewById<TextView>(R.id.tvTrendDayLabel).text =
                day.format(WEEKDAY_FORMATTER)

            setTrendBarHeight(
                itemView.findViewById(R.id.viewTrendRunningBar),
                totals.runningSeconds,
                maxSeconds
            )
            setTrendBarHeight(
                itemView.findViewById(R.id.viewTrendWalkingBar),
                totals.walkingSeconds,
                maxSeconds
            )
            setTrendBarHeight(
                itemView.findViewById(R.id.viewTrendCyclingBar),
                totals.cyclingSeconds,
                maxSeconds
            )

            weeklyTrendChartContainer.addView(
                itemView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index < days.lastIndex) {
                        marginEnd = dpToPx(4)
                    }
                }
            )
        }
    }

    private fun setTrendBarHeight(view: View, valueSeconds: Int, maxSeconds: Int) {
        val params = view.layoutParams
        params.height = when {
            valueSeconds <= 0 || maxSeconds <= 0 -> dpToPx(2)
            else -> {
                val scaledHeight = (valueSeconds.toFloat() / maxSeconds.toFloat()) * dpToPx(92)
                scaledHeight.toInt().coerceAtLeast(dpToPx(8))
            }
        }
        view.layoutParams = params
        view.alpha = if (valueSeconds <= 0) 0.18f else 1f
    }

    private fun parseSessionDate(rawDateTime: String): LocalDate? {
        return try {
            LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    private fun isOutdoorTrendType(workoutType: String): Boolean {
        return workoutType == "running" || workoutType == "walking" || workoutType == "cycling"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showLoadingState() {
        loadingText.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        totalsCard.visibility = View.GONE
        trendsCard.visibility = View.GONE
        bodyCard.visibility = View.GONE
        summaryCard.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        loadingText.visibility = View.GONE
        totalsCard.visibility = View.VISIBLE
        trendsCard.visibility = View.VISIBLE
        bodyCard.visibility = View.VISIBLE
        summaryCard.visibility = View.VISIBLE
        emptyText.visibility = View.VISIBLE
        if (message.isNotBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        renderReport(emptyList(), getSharedPreferences("UserPrefs", Context.MODE_PRIVATE))
    }

    private fun calculateBmi(weightKg: Int, heightCm: Int): Double {
        val heightMeters = heightCm / 100.0
        return weightKg / heightMeters.pow(2)
    }

    private fun calculateHealthyWeightRange(heightCm: Int): Pair<Double, Double> {
        val heightMeters = heightCm / 100.0
        val minWeight = 18.5 * heightMeters.pow(2)
        val maxWeight = 24.9 * heightMeters.pow(2)
        return Pair(minWeight, maxWeight)
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

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        }
    }

    private fun formatCalories(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f kcal", value)
    }

    private fun isWithinLastDays(rawDateTime: String, days: Long): Boolean {
        return try {
            val parsed = LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER)
            parsed.isAfter(LocalDateTime.now().minusDays(days))
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val HISTORY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    }
}

data class TrendDayTotals(
    var runningSeconds: Int = 0,
    var walkingSeconds: Int = 0,
    var cyclingSeconds: Int = 0
)
