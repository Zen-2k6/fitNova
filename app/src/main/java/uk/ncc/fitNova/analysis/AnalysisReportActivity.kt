package uk.ncc.fitNova.analysis

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.ncc.fitNova.R
import uk.ncc.fitNova.data.prefs.SessionPrefs
import uk.ncc.fitNova.data.prefs.SessionSnapshot
import uk.ncc.fitNova.data.remote.BackendConfig
import uk.ncc.fitNova.ui.applyBlackSystemBars
import uk.ncc.fitNova.workout.WorkoutHistorySession
import uk.ncc.fitNova.workout.WorkoutJsonParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

class AnalysisReportActivity : AppCompatActivity() {
    private lateinit var backButton: MaterialButton
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var filtersCard: View
    private lateinit var overviewCard: View
    private lateinit var trendsCard: View
    private lateinit var bodyCard: View
    private lateinit var summaryCard: View
    private lateinit var overviewTitle: TextView
    private lateinit var chartTitle: TextView
    private lateinit var selectedPeriodText: TextView
    private lateinit var previousPeriodButton: MaterialButton
    private lateinit var nextPeriodButton: MaterialButton
    private lateinit var trendEmptyText: TextView
    private lateinit var trendChartScroll: HorizontalScrollView
    private lateinit var trendChartContainer: LinearLayout
    private lateinit var bmiValue: TextView
    private lateinit var waterGoalValue: TextView
    private lateinit var healthyRangeValue: TextView
    private lateinit var summaryBody: TextView
    private lateinit var statLabelViews: List<TextView>
    private lateinit var statValueViews: List<TextView>
    private lateinit var activityButtons: Map<AnalysisActivityType, MaterialButton>
    private lateinit var periodButtons: Map<AnalysisPeriodType, MaterialButton>
    private lateinit var metricDropdown: MaterialAutoCompleteTextView

    private val sessionPrefs by lazy { SessionPrefs(this) }
    private var sessionSnapshot: SessionSnapshot? = null
    private var allSessions: List<WorkoutHistorySession> = emptyList()
    private var selectedActivity = AnalysisActivityType.RUNNING
    private var selectedPeriod = AnalysisPeriodType.WEEK
    private var selectedMetric = AnalysisMetricType.DISTANCE
    private var selectedWeekOffset = 0
    private var selectedMonthOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analysis_report)
        applyBlackSystemBars(this)
        applySystemBarInsets(findViewById(R.id.main))

        bindViews()
        bindActions()
        updateSelectors()
        loadReport()
    }

    private fun bindViews() {
        backButton = findViewById(R.id.btnBackAnalysis)
        loadingText = findViewById(R.id.tvAnalysisLoading)
        emptyText = findViewById(R.id.tvAnalysisEmpty)
        filtersCard = findViewById(R.id.cardAnalysisFilters)
        overviewCard = findViewById(R.id.cardAnalysisOverview)
        trendsCard = findViewById(R.id.cardAnalysisTrends)
        bodyCard = findViewById(R.id.cardAnalysisBody)
        summaryCard = findViewById(R.id.cardAnalysisSummary)
        overviewTitle = findViewById(R.id.tvAnalysisOverviewTitle)
        chartTitle = findViewById(R.id.tvAnalysisChartTitle)
        selectedPeriodText = findViewById(R.id.tvAnalysisSelectedPeriod)
        previousPeriodButton = findViewById(R.id.btnAnalysisPreviousPeriod)
        nextPeriodButton = findViewById(R.id.btnAnalysisNextPeriod)
        trendEmptyText = findViewById(R.id.tvAnalysisTrendEmpty)
        trendChartScroll = findViewById(R.id.scrollAnalysisTrendChart)
        trendChartContainer = findViewById(R.id.llAnalysisTrendChart)
        bmiValue = findViewById(R.id.tvAnalysisBmiValue)
        waterGoalValue = findViewById(R.id.tvAnalysisWaterGoalValue)
        healthyRangeValue = findViewById(R.id.tvAnalysisHealthyRangeValue)
        summaryBody = findViewById(R.id.tvAnalysisSummaryBody)

        statLabelViews = listOf(
            findViewById(R.id.tvAnalysisStatOneLabel),
            findViewById(R.id.tvAnalysisStatTwoLabel),
            findViewById(R.id.tvAnalysisStatThreeLabel),
            findViewById(R.id.tvAnalysisStatFourLabel)
        )
        statValueViews = listOf(
            findViewById(R.id.tvAnalysisStatOneValue),
            findViewById(R.id.tvAnalysisStatTwoValue),
            findViewById(R.id.tvAnalysisStatThreeValue),
            findViewById(R.id.tvAnalysisStatFourValue)
        )

        activityButtons = mapOf(
            AnalysisActivityType.RUNNING to findViewById(R.id.btnAnalysisActivityRunning),
            AnalysisActivityType.WALKING to findViewById(R.id.btnAnalysisActivityWalking),
            AnalysisActivityType.WEIGHT_LIFTING to findViewById(R.id.btnAnalysisActivityWeight),
            AnalysisActivityType.CYCLING to findViewById(R.id.btnAnalysisActivityCycling)
        )

        periodButtons = mapOf(
            AnalysisPeriodType.WEEK to findViewById(R.id.btnAnalysisPeriodWeek),
            AnalysisPeriodType.MONTH to findViewById(R.id.btnAnalysisPeriodMonth)
        )

        metricDropdown = findViewById(R.id.dropdownAnalysisMetric)
    }

    private fun bindActions() {
        backButton.setOnClickListener { finish() }

        activityButtons.forEach { (activity, button) ->
            button.setOnClickListener {
                if (selectedActivity == activity) {
                    return@setOnClickListener
                }
                selectedActivity = activity
                syncMetricSelection()
                updateSelectors()
                renderLoadedState()
            }
        }

        periodButtons.forEach { (period, button) ->
            button.setOnClickListener {
                if (selectedPeriod == period) {
                    return@setOnClickListener
                }
                selectedPeriod = period
                updateSelectors()
                renderLoadedState()
            }
        }

        metricDropdown.setOnItemClickListener { _, _, position, _ ->
            val metrics = metricsForSelectedActivity()
            val metric = metrics.getOrNull(position) ?: return@setOnItemClickListener
            if (selectedMetric == metric) {
                return@setOnItemClickListener
            }
            selectedMetric = metric
            updateSelectors()
            renderLoadedState()
        }

        previousPeriodButton.setOnClickListener {
            when (selectedPeriod) {
                AnalysisPeriodType.WEEK -> selectedWeekOffset += 1
                AnalysisPeriodType.MONTH -> selectedMonthOffset += 1
            }
            updateSelectors()
            renderLoadedState()
        }

        nextPeriodButton.setOnClickListener {
            when (selectedPeriod) {
                AnalysisPeriodType.WEEK -> {
                    if (selectedWeekOffset > 0) {
                        selectedWeekOffset -= 1
                    }
                }

                AnalysisPeriodType.MONTH -> {
                    if (selectedMonthOffset > 0) {
                        selectedMonthOffset -= 1
                    }
                }
            }
            updateSelectors()
            renderLoadedState()
        }
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
        val session = sessionPrefs.read()
        sessionSnapshot = session
        val userId = session.userId
        if (userId <= 0) {
            Toast.makeText(this, R.string.profile_session_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoadingState()
        renderBodySignals(session)

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

                    allSessions = decodeSessions(payload.optJSONArray("sessions") ?: JSONArray())
                    renderLoadedState()
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
                    "limit" to "200"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        return WorkoutJsonParser.decodeSessions(array)
    }

    private fun renderLoadedState() {
        val snapshot = sessionSnapshot ?: sessionPrefs.read()
        val selectedSessions = allSessions.filter { it.workoutType == selectedActivity.workoutType }

        loadingText.visibility = View.GONE
        filtersCard.visibility = View.VISIBLE
        overviewCard.visibility = View.VISIBLE
        trendsCard.visibility = View.VISIBLE
        bodyCard.visibility = View.VISIBLE
        summaryCard.visibility = View.VISIBLE
        emptyText.visibility = if (allSessions.isEmpty() || selectedSessions.isEmpty()) View.VISIBLE else View.GONE

        emptyText.text = when {
            allSessions.isEmpty() -> getString(R.string.analysis_report_empty)
            else -> getString(
                R.string.analysis_report_empty_activity,
                getActivityLabel(selectedActivity).lowercase(Locale.getDefault())
            )
        }

        updateSelectors()
        renderOverview(selectedSessions)
        renderTrendChart(selectedSessions)
        renderBodySignals(snapshot)
        summaryBody.text = buildActivitySummary(selectedSessions)
    }

    private fun renderOverview(sessions: List<WorkoutHistorySession>) {
        overviewTitle.text = getString(
            R.string.analysis_report_overview_title,
            getActivityLabel(selectedActivity)
        )

        val stats = buildOverviewStats(sessions)
        stats.forEachIndexed { index, stat ->
            statLabelViews[index].text = stat.label
            statValueViews[index].text = stat.value
        }
    }

    private fun buildOverviewStats(sessions: List<WorkoutHistorySession>): List<AnalysisStat> {
        if (sessions.isEmpty()) {
            return metricsForSelectedActivity().map { metric ->
                AnalysisStat(getPrimaryStatLabel(metric), getString(R.string.analysis_report_unknown))
            }
        }

        return if (selectedActivity == AnalysisActivityType.WEIGHT_LIFTING) {
            listOf(
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_volume),
                    formatVolume(sessions.maxOfOrNull { it.totalVolume } ?: 0.0)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_longest_time),
                    formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_reps),
                    formatCount(sessions.maxOfOrNull { it.totalReps }?.toDouble() ?: 0.0, R.string.analysis_report_metric_reps)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_sets),
                    formatCount(sessions.maxOfOrNull { it.totalSets }?.toDouble() ?: 0.0, R.string.analysis_report_metric_sets)
                )
            )
        } else {
            listOf(
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_distance),
                    formatDistance(sessions.maxOfOrNull { it.distanceMeters } ?: 0.0)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_longest_time),
                    formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_calories),
                    formatCalories(sessions.maxOfOrNull { it.caloriesBurned } ?: 0.0)
                ),
                AnalysisStat(
                    getString(R.string.analysis_report_stat_top_speed),
                    formatSpeed(sessions.maxOfOrNull { averageSpeedKph(it) } ?: 0.0)
                )
            )
        }
    }

    private fun renderTrendChart(sessions: List<WorkoutHistorySession>) {
        val chartBuckets = buildTrendBuckets(sessions)
        val maxValue = chartBuckets.maxOfOrNull { it.value } ?: 0.0
        val hasData = chartBuckets.any { it.value > 0.0 }

        chartTitle.text = getString(
            R.string.analysis_report_chart_title,
            getActivityLabel(selectedActivity),
            getMetricLabel(selectedMetric)
        )
        selectedPeriodText.text = getSelectedPeriodLabel()
        trendChartContainer.removeAllViews()
        trendChartScroll.visibility = if (hasData) View.VISIBLE else View.GONE
        trendEmptyText.visibility = if (hasData) View.GONE else View.VISIBLE
        trendEmptyText.text = getString(
            R.string.analysis_report_chart_empty,
            getActivityLabel(selectedActivity).lowercase(Locale.getDefault()),
            getRangeLabel().lowercase(Locale.getDefault())
        )

        if (!hasData) {
            return
        }

        val barColor = ContextCompat.getColor(this, getActivityColor(selectedActivity))
        val inflater = LayoutInflater.from(this)
        val isDenseChart = chartBuckets.size > 10
        chartBuckets.forEachIndexed { index, bucket ->
            val itemView = inflater.inflate(
                R.layout.item_analysis_trend_bar,
                trendChartContainer,
                false
            )

            val valueLabel = itemView.findViewById<TextView>(R.id.tvTrendValueLabel)
            val dayLabel = itemView.findViewById<TextView>(R.id.tvTrendDayLabel)
            valueLabel.text = formatMetricShort(selectedMetric, bucket.value)
            valueLabel.visibility = if (isDenseChart) View.GONE else View.VISIBLE
            dayLabel.text = bucket.label
            dayLabel.visibility = if (shouldShowTrendLabel(index, chartBuckets.size)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

            val barView = itemView.findViewById<View>(R.id.viewTrendBar)
            val params = barView.layoutParams
            params.height = when {
                bucket.value <= 0.0 || maxValue <= 0.0 -> dpToPx(4)
                else -> {
                    val scaledHeight = (bucket.value / maxValue) * dpToPx(110)
                    scaledHeight.toInt().coerceAtLeast(dpToPx(10))
                }
            }
            barView.layoutParams = params
            barView.setBackgroundColor(barColor)
            barView.alpha = if (bucket.value <= 0.0) 0.15f else 1f

            val layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.weight = 1f
            if (index < chartBuckets.lastIndex) {
                layoutParams.marginEnd = if (isDenseChart) dpToPx(2) else dpToPx(6)
            }
            trendChartContainer.addView(itemView, layoutParams)
        }

        trendChartScroll.post {
            trendChartScroll.scrollTo(0, 0)
        }
    }

    private fun buildTrendBuckets(sessions: List<WorkoutHistorySession>): List<TrendBucket> {
        return when (selectedPeriod) {
            AnalysisPeriodType.WEEK -> {
                val start = currentWeekStart().minusWeeks(selectedWeekOffset.toLong())
                (0..6).map { index ->
                    val day = start.plusDays(index.toLong())
                    TrendBucket(
                        label = day.format(WEEKDAY_FORMATTER),
                        value = aggregateMetricForDay(sessions, day)
                    )
                }
            }

            AnalysisPeriodType.MONTH -> {
                val yearMonth = YearMonth.now().minusMonths(selectedMonthOffset.toLong())
                (1..yearMonth.lengthOfMonth()).map { dayNumber ->
                    val day = yearMonth.atDay(dayNumber)
                    TrendBucket(
                        label = day.dayOfMonth.toString(),
                        value = aggregateMetricForDay(sessions, day)
                    )
                }
            }
        }
    }

    private fun aggregateMetricForDay(
        sessions: List<WorkoutHistorySession>,
        day: LocalDate
    ): Double {
        val daySessions = sessions.filter { parseSessionDate(it.createdAt) == day }
        if (daySessions.isEmpty()) {
            return 0.0
        }

        return when (selectedMetric) {
            AnalysisMetricType.DISTANCE -> daySessions.sumOf { it.distanceMeters }
            AnalysisMetricType.DURATION -> daySessions.sumOf { it.durationSeconds }.toDouble()
            AnalysisMetricType.CALORIES -> daySessions.sumOf { it.caloriesBurned }
            AnalysisMetricType.SPEED -> daySessions.maxOfOrNull { averageSpeedKph(it) } ?: 0.0
            AnalysisMetricType.VOLUME -> daySessions.sumOf { it.totalVolume }
            AnalysisMetricType.REPS -> daySessions.sumOf { it.totalReps }.toDouble()
            AnalysisMetricType.SETS -> daySessions.sumOf { it.totalSets }.toDouble()
        }
    }

    private fun renderBodySignals(session: SessionSnapshot) {
        val weight = session.weight
        val height = session.height

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

    private fun buildActivitySummary(sessions: List<WorkoutHistorySession>): String {
        val activityLabel = getActivityLabel(selectedActivity)
        if (sessions.isEmpty()) {
            return getString(
                R.string.analysis_report_summary_empty_activity,
                activityLabel.lowercase(Locale.getDefault())
            )
        }

        val periodSessions = sessions.filter { session ->
            val date = parseSessionDate(session.createdAt) ?: return@filter false
            isDateInSelectedPeriod(date)
        }
        val metricValue = when (selectedMetric) {
            AnalysisMetricType.DISTANCE,
            AnalysisMetricType.DURATION,
            AnalysisMetricType.CALORIES,
            AnalysisMetricType.SPEED,
            AnalysisMetricType.VOLUME,
            AnalysisMetricType.REPS,
            AnalysisMetricType.SETS -> aggregateMetricForRange(periodSessions)
        }

        return if (selectedActivity == AnalysisActivityType.WEIGHT_LIFTING) {
            val bestVolume = formatVolume(sessions.maxOfOrNull { it.totalVolume } ?: 0.0)
            val longestTime = formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
            val topReps = formatCount(
                sessions.maxOfOrNull { it.totalReps }?.toDouble() ?: 0.0,
                R.string.analysis_report_metric_reps
            )
            val topSets = formatCount(
                sessions.maxOfOrNull { it.totalSets }?.toDouble() ?: 0.0,
                R.string.analysis_report_metric_sets
            )
            getString(
                R.string.analysis_report_summary_strength,
                activityLabel,
                sessions.size,
                bestVolume,
                longestTime,
                topReps,
                topSets,
                getSelectedPeriodLabel(),
                formatMetricValue(selectedMetric, metricValue),
                getMetricLabel(selectedMetric).lowercase(Locale.getDefault())
            )
        } else {
            val bestDistance = formatDistance(sessions.maxOfOrNull { it.distanceMeters } ?: 0.0)
            val longestTime = formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
            val topCalories = formatCalories(sessions.maxOfOrNull { it.caloriesBurned } ?: 0.0)
            val topSpeed = formatSpeed(sessions.maxOfOrNull { averageSpeedKph(it) } ?: 0.0)
            getString(
                R.string.analysis_report_summary_outdoor,
                activityLabel,
                sessions.size,
                bestDistance,
                longestTime,
                topCalories,
                topSpeed,
                getSelectedPeriodLabel(),
                formatMetricValue(selectedMetric, metricValue),
                getMetricLabel(selectedMetric).lowercase(Locale.getDefault())
            )
        }
    }

    private fun aggregateMetricForRange(sessions: List<WorkoutHistorySession>): Double {
        if (sessions.isEmpty()) {
            return 0.0
        }

        return when (selectedMetric) {
            AnalysisMetricType.DISTANCE -> sessions.sumOf { it.distanceMeters }
            AnalysisMetricType.DURATION -> sessions.sumOf { it.durationSeconds }.toDouble()
            AnalysisMetricType.CALORIES -> sessions.sumOf { it.caloriesBurned }
            AnalysisMetricType.SPEED -> sessions.maxOfOrNull { averageSpeedKph(it) } ?: 0.0
            AnalysisMetricType.VOLUME -> sessions.sumOf { it.totalVolume }
            AnalysisMetricType.REPS -> sessions.sumOf { it.totalReps }.toDouble()
            AnalysisMetricType.SETS -> sessions.sumOf { it.totalSets }.toDouble()
        }
    }

    private fun updateSelectors() {
        activityButtons.forEach { (activity, button) ->
            styleSelectorButton(button, selectedActivity == activity)
        }

        periodButtons.forEach { (period, button) ->
            styleSelectorButton(button, selectedPeriod == period)
        }

        val availableMetrics = metricsForSelectedActivity()
        val metricLabels = availableMetrics.map(::getMetricLabel)
        metricDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                metricLabels
            )
        )
        val selectedIndex = availableMetrics.indexOf(selectedMetric).coerceAtLeast(0)
        metricDropdown.setText(metricLabels[selectedIndex], false)

        val hasPreviousPeriod = when (selectedPeriod) {
            AnalysisPeriodType.WEEK -> selectedWeekOffset > 0
            AnalysisPeriodType.MONTH -> selectedMonthOffset > 0
        }
        nextPeriodButton.isEnabled = hasPreviousPeriod
        nextPeriodButton.alpha = if (hasPreviousPeriod) 1f else 0.45f
        previousPeriodButton.alpha = 1f
        selectedPeriodText.text = getSelectedPeriodLabel()
    }

    private fun syncMetricSelection() {
        val metrics = metricsForSelectedActivity()
        if (selectedMetric !in metrics) {
            selectedMetric = metrics.first()
        }
    }

    private fun styleSelectorButton(button: MaterialButton, isSelected: Boolean) {
        val backgroundColor = ContextCompat.getColor(
            this,
            if (isSelected) R.color.auth_button_blue else R.color.auth_logo_chip
        )
        val strokeColor = ContextCompat.getColor(
            this,
            if (isSelected) R.color.auth_button_blue else R.color.auth_gold_stroke
        )
        val textColor = ContextCompat.getColor(
            this,
            if (isSelected) R.color.auth_gold_button_text else R.color.auth_gold_yellow
        )

        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.setTextColor(textColor)
    }

    private fun getSelectedPeriodLabel(): String {
        return when (selectedPeriod) {
            AnalysisPeriodType.WEEK -> {
                val start = currentWeekStart().minusWeeks(selectedWeekOffset.toLong())
                val end = start.plusDays(6)
                getString(
                    R.string.analysis_report_period_week_label,
                    start.format(SHORT_DATE_FORMATTER),
                    end.format(SHORT_DATE_FORMATTER)
                )
            }

            AnalysisPeriodType.MONTH -> {
                YearMonth.now()
                    .minusMonths(selectedMonthOffset.toLong())
                    .format(MONTH_FORMATTER)
            }
        }
    }

    private fun getRangeLabel(): String {
        return when (selectedPeriod) {
            AnalysisPeriodType.WEEK -> getString(R.string.analysis_report_period_week)
            AnalysisPeriodType.MONTH -> getString(R.string.analysis_report_period_month)
        }
    }

    private fun getActivityLabel(activity: AnalysisActivityType): String {
        return when (activity) {
            AnalysisActivityType.RUNNING -> getString(R.string.fitness_activity_running)
            AnalysisActivityType.WALKING -> getString(R.string.fitness_activity_walking)
            AnalysisActivityType.WEIGHT_LIFTING -> getString(R.string.fitness_activity_weight)
            AnalysisActivityType.CYCLING -> getString(R.string.fitness_activity_cycling)
        }
    }

    private fun metricsForSelectedActivity(): List<AnalysisMetricType> {
        return when (selectedActivity) {
            AnalysisActivityType.WEIGHT_LIFTING -> listOf(
                AnalysisMetricType.VOLUME,
                AnalysisMetricType.DURATION,
                AnalysisMetricType.REPS,
                AnalysisMetricType.SETS
            )

            else -> listOf(
                AnalysisMetricType.DISTANCE,
                AnalysisMetricType.DURATION,
                AnalysisMetricType.CALORIES,
                AnalysisMetricType.SPEED
            )
        }
    }

    private fun getPrimaryStatLabel(metric: AnalysisMetricType): String {
        return when (metric) {
            AnalysisMetricType.DISTANCE -> getString(R.string.analysis_report_stat_top_distance)
            AnalysisMetricType.DURATION -> getString(R.string.analysis_report_stat_longest_time)
            AnalysisMetricType.CALORIES -> getString(R.string.analysis_report_stat_top_calories)
            AnalysisMetricType.SPEED -> getString(R.string.analysis_report_stat_top_speed)
            AnalysisMetricType.VOLUME -> getString(R.string.analysis_report_stat_top_volume)
            AnalysisMetricType.REPS -> getString(R.string.analysis_report_stat_top_reps)
            AnalysisMetricType.SETS -> getString(R.string.analysis_report_stat_top_sets)
        }
    }

    private fun getMetricLabel(metric: AnalysisMetricType): String {
        return when (metric) {
            AnalysisMetricType.DISTANCE -> getString(R.string.analysis_report_metric_distance)
            AnalysisMetricType.DURATION -> getString(R.string.analysis_report_metric_duration)
            AnalysisMetricType.CALORIES -> getString(R.string.analysis_report_metric_calories)
            AnalysisMetricType.SPEED -> getString(R.string.analysis_report_metric_speed)
            AnalysisMetricType.VOLUME -> getString(R.string.analysis_report_metric_volume)
            AnalysisMetricType.REPS -> getString(R.string.analysis_report_metric_reps)
            AnalysisMetricType.SETS -> getString(R.string.analysis_report_metric_sets)
        }
    }

    private fun getActivityColor(activity: AnalysisActivityType): Int {
        return when (activity) {
            AnalysisActivityType.RUNNING -> R.color.auth_button_blue
            AnalysisActivityType.WALKING -> R.color.fitness_accent
            AnalysisActivityType.WEIGHT_LIFTING -> R.color.auth_gold_yellow_dark
            AnalysisActivityType.CYCLING -> R.color.red_brown
        }
    }

    private fun showLoadingState() {
        loadingText.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        filtersCard.visibility = View.GONE
        overviewCard.visibility = View.GONE
        trendsCard.visibility = View.GONE
        bodyCard.visibility = View.GONE
        summaryCard.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        allSessions = emptyList()
        loadingText.visibility = View.GONE
        if (message.isNotBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        renderLoadedState()
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

    private fun parseSessionDate(rawDateTime: String): LocalDate? {
        return try {
            LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    private fun currentWeekStart(): LocalDate {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun shouldShowTrendLabel(index: Int, total: Int): Boolean {
        if (total <= 10) {
            return true
        }
        val step = when {
            total <= 16 -> 2
            total <= 24 -> 4
            else -> 5
        }
        return index == 0 || index == total - 1 || index % step == 0
    }

    private fun isDateInSelectedPeriod(date: LocalDate): Boolean {
        return when (selectedPeriod) {
            AnalysisPeriodType.WEEK -> {
                val start = currentWeekStart().minusWeeks(selectedWeekOffset.toLong())
                val end = start.plusDays(6)
                !date.isBefore(start) && !date.isAfter(end)
            }

            AnalysisPeriodType.MONTH -> {
                val yearMonth = YearMonth.now().minusMonths(selectedMonthOffset.toLong())
                YearMonth.from(date) == yearMonth
            }
        }
    }

    private fun averageSpeedKph(session: WorkoutHistorySession): Double {
        if (session.durationSeconds <= 0 || session.distanceMeters <= 0.0) {
            return 0.0
        }
        return (session.distanceMeters / session.durationSeconds) * 3.6
    }

    private fun formatMetricValue(metric: AnalysisMetricType, value: Double): String {
        return when (metric) {
            AnalysisMetricType.DISTANCE -> formatDistance(value)
            AnalysisMetricType.DURATION -> formatDuration(value.roundToInt())
            AnalysisMetricType.CALORIES -> formatCalories(value)
            AnalysisMetricType.SPEED -> formatSpeed(value)
            AnalysisMetricType.VOLUME -> formatVolume(value)
            AnalysisMetricType.REPS -> formatCount(value, R.string.analysis_report_metric_reps)
            AnalysisMetricType.SETS -> formatCount(value, R.string.analysis_report_metric_sets)
        }
    }

    private fun formatMetricShort(metric: AnalysisMetricType, value: Double): String {
        return when (metric) {
            AnalysisMetricType.DISTANCE -> {
                if (value >= 1000.0) {
                    getString(R.string.analysis_report_chart_bar_distance_km, value / 1000.0)
                } else {
                    getString(R.string.analysis_report_chart_bar_distance_m, value)
                }
            }

            AnalysisMetricType.DURATION -> {
                val totalMinutes = (value / 60.0).roundToInt()
                if (totalMinutes >= 60) {
                    getString(
                        R.string.analysis_report_chart_bar_duration_hours,
                        totalMinutes / 60
                    )
                } else {
                    getString(
                        R.string.analysis_report_chart_bar_duration_minutes,
                        totalMinutes
                    )
                }
            }

            AnalysisMetricType.CALORIES -> getString(
                R.string.analysis_report_chart_bar_calories,
                value
            )

            AnalysisMetricType.SPEED -> getString(
                R.string.analysis_report_chart_bar_speed,
                value
            )

            AnalysisMetricType.VOLUME -> getString(
                R.string.analysis_report_chart_bar_volume,
                value
            )

            AnalysisMetricType.REPS,
            AnalysisMetricType.SETS -> getString(
                R.string.analysis_report_chart_bar_count,
                value
            )
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

    private fun formatSpeed(speedKph: Double): String {
        return String.format(Locale.getDefault(), "%.1f km/h", speedKph)
    }

    private fun formatVolume(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f kg", value)
    }

    private fun formatCount(value: Double, labelRes: Int): String {
        return getString(
            R.string.analysis_report_count_value,
            value.roundToInt(),
            getString(labelRes).lowercase(Locale.getDefault())
        )
    }

    private fun buildLabelLine(labelRes: Int, value: String): String {
        return "${getString(labelRes)}: $value"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private val HISTORY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        private val SHORT_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        private val MONTH_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
}

private enum class AnalysisActivityType(val workoutType: String) {
    RUNNING("running"),
    WALKING("walking"),
    WEIGHT_LIFTING("weight_lifting"),
    CYCLING("cycling")
}

private enum class AnalysisPeriodType {
    WEEK,
    MONTH
}

private enum class AnalysisMetricType {
    DISTANCE,
    DURATION,
    CALORIES,
    SPEED,
    VOLUME,
    REPS,
    SETS
}

private data class AnalysisStat(
    val label: String,
    val value: String
)

private data class TrendBucket(
    val label: String,
    val value: Double
)
