package uk.ncc.fitNova

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

class FitnessActivity : AppCompatActivity() {
    private lateinit var greetingText: TextView
    private lateinit var profileMetaText: TextView
    private lateinit var weightValueText: TextView
    private lateinit var heightValueText: TextView
    private lateinit var bmiValueText: TextView
    private lateinit var bmiCategoryText: TextView
    private lateinit var weightRangeText: TextView
    private lateinit var waterGoalText: TextView
    private lateinit var tipText: TextView
    private lateinit var profileButton: View
    private lateinit var scrollFitnessContent: ScrollView
    private lateinit var homeSection: View
    private lateinit var activitiesSection: View
    private lateinit var activitySessionCardsContainer: LinearLayout
    private lateinit var historySection: View
    private lateinit var reportSection: View
    private lateinit var homeTabView: View
    private lateinit var activitiesTabView: View
    private lateinit var historyTabView: View
    private lateinit var reportTabView: View
    private lateinit var homeTabLabel: TextView
    private lateinit var activitiesTabLabel: TextView
    private lateinit var historyTabLabel: TextView
    private lateinit var reportTabLabel: TextView
    private lateinit var homeTabIndicator: View
    private lateinit var activitiesTabIndicator: View
    private lateinit var historyTabIndicator: View
    private lateinit var reportTabIndicator: View

    private lateinit var historyLoadingText: TextView
    private lateinit var historyEmptyText: TextView
    private lateinit var historyContainer: LinearLayout

    private lateinit var reportLoadingText: TextView
    private lateinit var reportEmptyText: TextView
    private lateinit var reportTotalsCard: View
    private lateinit var reportTrendsCard: View
    private lateinit var reportBodyCard: View
    private lateinit var reportSummaryCard: View
    private lateinit var reportTotalSessionsValue: TextView
    private lateinit var reportTotalTimeValue: TextView
    private lateinit var reportTotalDistanceValue: TextView
    private lateinit var reportTotalCaloriesValue: TextView
    private lateinit var reportBestActivityValue: TextView
    private lateinit var reportAverageSessionValue: TextView
    private lateinit var reportLongestSessionValue: TextView
    private lateinit var reportLast7DaysValue: TextView
    private lateinit var reportWeeklyTrendEmptyText: TextView
    private lateinit var reportWeeklyTrendChartContainer: LinearLayout
    private lateinit var reportBmiValue: TextView
    private lateinit var reportWaterGoalValue: TextView
    private lateinit var reportHealthyRangeValue: TextView
    private lateinit var reportSummaryBody: TextView

    private var selectedTab = TAB_HOME
    private var workoutSessions: List<WorkoutHistorySession> = emptyList()
    private var hasLoadedWorkoutData = false
    private var isWorkoutDataLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fitness)

        bindViews()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindActions()
        selectedTab = savedInstanceState?.getString(KEY_SELECTED_TAB) ?: TAB_HOME
        showSelectedTab(scrollToTop = false)
    }

    override fun onResume() {
        super.onResume()
        renderDashboardFromSession()
        hasLoadedWorkoutData = false
        if (selectedTab == TAB_HISTORY || selectedTab == TAB_REPORT) {
            ensureWorkoutDataLoaded(force = true)
        }
        syncBottomNavSelection()
    }

    private fun bindViews() {
        greetingText = findViewById(R.id.tvGreeting)
        profileMetaText = findViewById(R.id.tvProfileMeta)
        weightValueText = findViewById(R.id.tvWeightValue)
        heightValueText = findViewById(R.id.tvHeightValue)
        bmiValueText = findViewById(R.id.tvBmiValue)
        bmiCategoryText = findViewById(R.id.tvBmiCategory)
        weightRangeText = findViewById(R.id.tvWeightRangeValue)
        waterGoalText = findViewById(R.id.tvWaterGoalValue)
        tipText = findViewById(R.id.tvCoachingTip)
        profileButton = findViewById(R.id.btnProfile)
        scrollFitnessContent = findViewById(R.id.scrollFitnessContent)
        homeSection = findViewById(R.id.layoutHomeSection)
        activitiesSection = findViewById(R.id.layoutActivitiesSection)
        activitySessionCardsContainer = findViewById(R.id.llActivitySessionCards)
        historySection = findViewById(R.id.layoutHistorySection)
        reportSection = findViewById(R.id.layoutReportSection)
        homeTabView = findViewById(R.id.tabDashboardHome)
        activitiesTabView = findViewById(R.id.tabDashboardActivities)
        historyTabView = findViewById(R.id.tabDashboardHistory)
        reportTabView = findViewById(R.id.tabDashboardReport)
        homeTabLabel = findViewById(R.id.tvDashboardHome)
        activitiesTabLabel = findViewById(R.id.tvDashboardActivities)
        historyTabLabel = findViewById(R.id.tvDashboardHistory)
        reportTabLabel = findViewById(R.id.tvDashboardReport)
        homeTabIndicator = findViewById(R.id.indicatorDashboardHome)
        activitiesTabIndicator = findViewById(R.id.indicatorDashboardActivities)
        historyTabIndicator = findViewById(R.id.indicatorDashboardHistory)
        reportTabIndicator = findViewById(R.id.indicatorDashboardReport)

        historyLoadingText = findViewById(R.id.tvDashboardHistoryLoading)
        historyEmptyText = findViewById(R.id.tvDashboardHistoryEmpty)
        historyContainer = findViewById(R.id.llDashboardHistoryItems)

        reportLoadingText = findViewById(R.id.tvDashboardReportLoading)
        reportEmptyText = findViewById(R.id.tvDashboardReportEmpty)
        reportTotalsCard = findViewById(R.id.cardDashboardAnalysisTotals)
        reportTrendsCard = findViewById(R.id.cardDashboardAnalysisTrends)
        reportBodyCard = findViewById(R.id.cardDashboardAnalysisBody)
        reportSummaryCard = findViewById(R.id.cardDashboardAnalysisSummary)
        reportTotalSessionsValue = findViewById(R.id.tvDashboardAnalysisTotalSessionsValue)
        reportTotalTimeValue = findViewById(R.id.tvDashboardAnalysisTotalTimeValue)
        reportTotalDistanceValue = findViewById(R.id.tvDashboardAnalysisDistanceValue)
        reportTotalCaloriesValue = findViewById(R.id.tvDashboardAnalysisCaloriesValue)
        reportBestActivityValue = findViewById(R.id.tvDashboardAnalysisBestActivityValue)
        reportAverageSessionValue = findViewById(R.id.tvDashboardAnalysisAverageSessionValue)
        reportLongestSessionValue = findViewById(R.id.tvDashboardAnalysisLongestSessionValue)
        reportLast7DaysValue = findViewById(R.id.tvDashboardAnalysisLast7DaysValue)
        reportWeeklyTrendEmptyText = findViewById(R.id.tvDashboardAnalysisWeeklyTrendEmpty)
        reportWeeklyTrendChartContainer = findViewById(R.id.llDashboardAnalysisWeeklyTrendChart)
        reportBmiValue = findViewById(R.id.tvDashboardAnalysisBmiValue)
        reportWaterGoalValue = findViewById(R.id.tvDashboardAnalysisWaterGoalValue)
        reportHealthyRangeValue = findViewById(R.id.tvDashboardAnalysisHealthyRangeValue)
        reportSummaryBody = findViewById(R.id.tvDashboardAnalysisSummaryBody)

        renderActivitySessionCards()
    }

    private fun bindActions() {
        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        homeTabView.setOnClickListener {
            selectedTab = TAB_HOME
            showSelectedTab()
        }
        activitiesTabView.setOnClickListener {
            selectedTab = TAB_ACTIVITIES
            showSelectedTab()
        }
        historyTabView.setOnClickListener {
            selectedTab = TAB_HISTORY
            showSelectedTab()
        }
        reportTabView.setOnClickListener {
            selectedTab = TAB_REPORT
            showSelectedTab()
        }
    }

    private fun renderActivitySessionCards() {
        activitySessionCardsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val activityCards = listOf(
            ActivitySessionCard(
                workoutType = WorkoutNavigation.TYPE_RUNNING,
                titleRes = R.string.fitness_activity_running,
                bodyRes = R.string.fitness_running_body,
                overlayColorRes = R.color.dashboard_activity_running,
                imageRes = R.drawable.activity_running
            ),
            ActivitySessionCard(
                workoutType = WorkoutNavigation.TYPE_WALKING,
                titleRes = R.string.fitness_activity_walking,
                bodyRes = R.string.fitness_walking_body,
                overlayColorRes = R.color.dashboard_activity_walking,
                imageRes = R.drawable.activity_walking
            ),
            ActivitySessionCard(
                workoutType = WorkoutNavigation.TYPE_WEIGHT_LIFTING,
                titleRes = R.string.fitness_activity_weight,
                bodyRes = R.string.fitness_weight_body,
                overlayColorRes = R.color.dashboard_activity_weight,
                imageRes = R.drawable.activity_weight
            ),
            ActivitySessionCard(
                workoutType = WorkoutNavigation.TYPE_CYCLING,
                titleRes = R.string.fitness_activity_cycling,
                bodyRes = R.string.fitness_cycling_body,
                overlayColorRes = R.color.dashboard_activity_cycling,
                imageRes = R.drawable.activity_cycling
            )
        )

        activityCards.forEach { card ->
            val itemView = inflater.inflate(
                R.layout.item_activity_session_card,
                activitySessionCardsContainer,
                false
            )

            itemView.findViewById<TextView>(R.id.tvSessionActivityName).text = getString(card.titleRes)
            itemView.findViewById<TextView>(R.id.tvSessionActivityDescription).text =
                getString(card.bodyRes)
            itemView.findViewById<View>(R.id.viewSessionImageOverlay).setBackgroundColor(
                ContextCompat.getColor(this, card.overlayColorRes)
            )
            itemView.findViewById<ImageView>(R.id.imgSessionActivity).apply {
                setImageResource(card.imageRes)
                contentDescription = getString(card.titleRes)
            }

            val startListener = View.OnClickListener {
                startWorkout(card.workoutType)
            }
            itemView.setOnClickListener(startListener)
            itemView.findViewById<View>(R.id.btnStartSessionActivity)
                .setOnClickListener(startListener)

            activitySessionCardsContainer.addView(itemView)
        }
    }

    private fun renderDashboardFromSession() {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("IS_LOGGED_IN", false)) {
            Toast.makeText(this, R.string.fitness_login_required, Toast.LENGTH_SHORT).show()
            openLoginScreen(clearTask = true)
            return
        }

        val fullName = sharedPref.getString("Full_name", null).orEmpty().ifBlank {
            getString(R.string.fitness_unknown_name)
        }
        val email = sharedPref.getString("Email", "").orEmpty()
        val gender = sharedPref.getString("Gender", "").orEmpty()
        val age = sharedPref.getInt("Age", 0)
        val weight = sharedPref.getInt("Weight", 0)
        val height = sharedPref.getInt("Height", 0)

        renderHomeDashboard(fullName, email, gender, age, weight, height)
    }

    private fun renderHomeDashboard(
        fullName: String,
        email: String,
        gender: String,
        age: Int,
        weight: Int,
        height: Int
    ) {
        greetingText.text = getString(R.string.fitness_greeting_name, fullName)
        profileMetaText.text = buildProfileMeta(email = email, gender = gender, age = age)

        if (weight <= 0 || height <= 0) {
            weightValueText.text = getString(R.string.fitness_metric_unknown)
            heightValueText.text = getString(R.string.fitness_metric_unknown)
            bmiValueText.text = getString(R.string.fitness_metric_unknown)
            bmiCategoryText.text = getString(R.string.fitness_bmi_unknown)
            weightRangeText.text = getString(R.string.fitness_metrics_unavailable)
            waterGoalText.text = getString(R.string.fitness_metrics_unavailable)
            tipText.text = getString(R.string.fitness_tip_missing)
            return
        }

        weightValueText.text = getString(R.string.fitness_weight_value, weight)
        heightValueText.text = getString(R.string.fitness_height_value, height)

        val bmi = calculateBmi(weight, height)
        val bmiCategory = getBmiCategory(bmi)
        val healthyRange = calculateHealthyWeightRange(height)
        val waterGoalLiters = calculateWaterGoal(weight)

        bmiValueText.text = getString(R.string.fitness_bmi_value, bmi)
        bmiCategoryText.text = getString(bmiCategory.first)
        weightRangeText.text = getString(
            R.string.fitness_weight_range_value,
            healthyRange.first,
            healthyRange.second
        )
        waterGoalText.text = getString(R.string.fitness_water_goal_value, waterGoalLiters)
        tipText.text = getString(bmiCategory.second)
    }

    private fun buildProfileMeta(email: String, gender: String, age: Int): String {
        val parts = mutableListOf<String>()

        if (email.isNotBlank()) {
            parts.add(email)
        }
        if (age > 0) {
            parts.add(getString(R.string.profile_age_short, age))
        }
        if (gender.isNotBlank()) {
            parts.add(gender)
        }

        return if (parts.isEmpty()) {
            getString(R.string.fitness_profile_meta_fallback)
        } else {
            parts.joinToString("  •  ")
        }
    }

    private fun showSelectedTab(scrollToTop: Boolean = true) {
        homeSection.visibility = if (selectedTab == TAB_HOME) View.VISIBLE else View.GONE
        activitiesSection.visibility = if (selectedTab == TAB_ACTIVITIES) View.VISIBLE else View.GONE
        historySection.visibility = if (selectedTab == TAB_HISTORY) View.VISIBLE else View.GONE
        reportSection.visibility = if (selectedTab == TAB_REPORT) View.VISIBLE else View.GONE

        syncBottomNavSelection()

        if (selectedTab == TAB_HISTORY || selectedTab == TAB_REPORT) {
            ensureWorkoutDataLoaded()
        }

        if (scrollToTop) {
            scrollFitnessContent.post {
                scrollFitnessContent.smoothScrollTo(0, 0)
            }
        }
    }

    private fun syncBottomNavSelection() {
        updateDashboardTab(homeTabView, homeTabLabel, homeTabIndicator, selectedTab == TAB_HOME)
        updateDashboardTab(
            activitiesTabView,
            activitiesTabLabel,
            activitiesTabIndicator,
            selectedTab == TAB_ACTIVITIES
        )
        updateDashboardTab(
            historyTabView,
            historyTabLabel,
            historyTabIndicator,
            selectedTab == TAB_HISTORY
        )
        updateDashboardTab(
            reportTabView,
            reportTabLabel,
            reportTabIndicator,
            selectedTab == TAB_REPORT
        )
    }

    private fun updateDashboardTab(
        tabView: View,
        labelView: TextView,
        indicatorView: View,
        isSelected: Boolean
    ) {
        labelView.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.auth_button_blue else R.color.auth_hint
            )
        )
        indicatorView.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        tabView.alpha = if (isSelected) 1f else 0.9f
    }

    private fun ensureWorkoutDataLoaded(force: Boolean = false) {
        if (isWorkoutDataLoading) {
            return
        }
        if (hasLoadedWorkoutData && !force) {
            renderHistorySection(workoutSessions)
            renderReportSection(workoutSessions)
            return
        }

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("User_id", -1)
        if (userId <= 0) {
            Toast.makeText(this, R.string.profile_session_missing, Toast.LENGTH_SHORT).show()
            openLoginScreen(clearTask = true)
            return
        }

        isWorkoutDataLoading = true
        showWorkoutDataLoadingState()

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                isWorkoutDataLoading = false
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        showWorkoutDataError(payload.optString("message"))
                        return@Listener
                    }

                    workoutSessions = decodeSessions(payload.optJSONArray("sessions") ?: JSONArray())
                    hasLoadedWorkoutData = true
                    renderHistorySection(workoutSessions)
                    renderReportSection(workoutSessions)
                } catch (_: JSONException) {
                    showWorkoutDataError(getString(R.string.workout_history_load_failed))
                }
            },
            Response.ErrorListener {
                isWorkoutDataLoading = false
                showWorkoutDataError(getString(R.string.workout_history_load_network_error))
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

    private fun showWorkoutDataLoadingState() {
        historyLoadingText.visibility = View.VISIBLE
        historyEmptyText.visibility = View.GONE
        historyContainer.removeAllViews()

        reportLoadingText.visibility = View.VISIBLE
        reportEmptyText.visibility = View.GONE
        reportTotalsCard.visibility = View.GONE
        reportTrendsCard.visibility = View.GONE
        reportBodyCard.visibility = View.GONE
        reportSummaryCard.visibility = View.GONE
    }

    private fun showWorkoutDataError(message: String) {
        if (selectedTab == TAB_HISTORY) {
            historyLoadingText.visibility = View.GONE
            historyEmptyText.visibility = View.VISIBLE
            historyContainer.removeAllViews()
        }

        if (selectedTab == TAB_REPORT) {
            reportLoadingText.visibility = View.GONE
            reportEmptyText.visibility = View.VISIBLE
            reportTotalsCard.visibility = View.GONE
            reportTrendsCard.visibility = View.GONE
            reportBodyCard.visibility = View.GONE
            reportSummaryCard.visibility = View.GONE
        }

        if (message.isNotBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        val sessions = mutableListOf<WorkoutHistorySession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val setEntries = decodeSetEntries(item.optString("setLogJson"))
            val routeSummary = decodeRouteSummary(item.optString("routeJson"))
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
                    destinationLat = parseNullableDouble(item, "destinationLat"),
                    destinationLng = parseNullableDouble(item, "destinationLng"),
                    remainingDistanceMeters = item.optDouble("remainingDistanceMeters"),
                    destinationReached = item.optString("destinationReached") == "1",
                    routePointCount = routeSummary.pointCount,
                    routeStartLat = routeSummary.firstPoint?.latitude,
                    routeStartLng = routeSummary.firstPoint?.longitude,
                    targetDurationSeconds = item.optInt("targetDurationSeconds"),
                    targetDurationReached = item.optString("targetDurationReached") == "1",
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

    private fun decodeRouteSummary(rawJson: String): RouteSummary {
        if (rawJson.isBlank()) {
            return RouteSummary()
        }

        return try {
            val array = JSONArray(rawJson)
            val firstPoint = array.optJSONObject(0)?.let { point ->
                val lat = point.optDouble("lat")
                val lng = point.optDouble("lng")
                LatLng(lat, lng)
            }
            RouteSummary(
                pointCount = array.length(),
                firstPoint = firstPoint
            )
        } catch (_: JSONException) {
            RouteSummary()
        }
    }

    private fun parseNullableDouble(item: JSONObject, key: String): Double? {
        if (item.isNull(key)) {
            return null
        }

        return item.optString(key).toDoubleOrNull()
    }

    private fun renderHistorySection(sessions: List<WorkoutHistorySession>) {
        historyLoadingText.visibility = View.GONE
        historyContainer.removeAllViews()

        if (sessions.isEmpty()) {
            historyEmptyText.visibility = View.VISIBLE
            return
        }

        historyEmptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        sessions.forEach { session ->
            val itemView = inflater.inflate(R.layout.item_workout_history, historyContainer, false)

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
                    formatCaloriesValue(session.caloriesBurned)
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
                outdoorStatusText.visibility = View.VISIBLE
                outdoorProgressBar.visibility = View.VISIBLE
                renderOutdoorProgress(session, outdoorStatusText, outdoorProgressBar)
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
                outdoorStatusText.visibility = View.GONE
                outdoorProgressBar.visibility = View.GONE
                setTitle.visibility = View.VISIBLE
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

    private fun renderReportSection(sessions: List<WorkoutHistorySession>) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val totalSessions = sessions.size
        val totalDurationSeconds = sessions.sumOf { it.durationSeconds }
        val totalDistanceMeters = sessions.sumOf { it.distanceMeters }
        val totalCalories = sessions.sumOf { it.caloriesBurned }
        val averageDurationSeconds = if (totalSessions > 0) totalDurationSeconds / totalSessions else 0
        val favoriteType = sessions.groupingBy { it.workoutType }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val longestSession = sessions.maxByOrNull { it.durationSeconds }
        val recentSessions = sessions.filter { isWithinLastDays(it.createdAt, 7) }
        val recentDurationSeconds = recentSessions.sumOf { it.durationSeconds }

        reportLoadingText.visibility = View.GONE
        reportEmptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        reportTotalsCard.visibility = View.VISIBLE
        reportTrendsCard.visibility = View.VISIBLE
        reportBodyCard.visibility = View.VISIBLE
        reportSummaryCard.visibility = View.VISIBLE

        reportTotalSessionsValue.text = totalSessions.toString()
        reportTotalTimeValue.text = formatDuration(totalDurationSeconds)
        reportTotalDistanceValue.text = formatDistance(totalDistanceMeters)
        reportTotalCaloriesValue.text = formatCalories(totalCalories)

        reportBestActivityValue.text = buildLabelLine(
            R.string.analysis_report_best_activity,
            favoriteType?.let { formatWorkoutType(it) } ?: getString(R.string.analysis_report_unknown)
        )
        reportAverageSessionValue.text = buildLabelLine(
            R.string.analysis_report_average_session,
            formatDuration(averageDurationSeconds)
        )
        reportLongestSessionValue.text = buildLabelLine(
            R.string.analysis_report_longest_session,
            if (longestSession == null) {
                getString(R.string.analysis_report_unknown)
            } else {
                "${formatWorkoutType(longestSession.workoutType)} • ${formatDuration(longestSession.durationSeconds)}"
            }
        )
        reportLast7DaysValue.text = buildLabelLine(
            R.string.analysis_report_last_7_days,
            if (recentSessions.isEmpty()) {
                getString(R.string.analysis_report_sessions_value, 0)
            } else {
                "${recentSessions.size} sessions • ${formatDuration(recentDurationSeconds)}"
            }
        )
        renderWeeklyTrendChart(sessions)
        renderReportBodySignals(sharedPref)
        reportSummaryBody.text = buildSummaryText(
            totalSessions = totalSessions,
            favoriteType = favoriteType,
            totalDistanceMeters = totalDistanceMeters,
            totalCalories = totalCalories,
            bmiLine = reportBmiValue.text.toString()
        )
    }

    private fun renderReportBodySignals(sharedPref: android.content.SharedPreferences) {
        val weight = sharedPref.getInt("Weight", 0)
        val height = sharedPref.getInt("Height", 0)

        if (weight <= 0 || height <= 0) {
            reportBmiValue.text = buildLabelLine(
                R.string.analysis_report_body_bmi,
                getString(R.string.analysis_report_unknown)
            )
            reportWaterGoalValue.text = buildLabelLine(
                R.string.analysis_report_body_water,
                getString(R.string.analysis_report_unknown)
            )
            reportHealthyRangeValue.text = buildLabelLine(
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

        reportBmiValue.text = buildLabelLine(
            R.string.analysis_report_body_bmi,
            "${String.format(Locale.getDefault(), "%.1f", bmi)} ($bmiLabel)"
        )
        reportWaterGoalValue.text = buildLabelLine(
            R.string.analysis_report_body_water,
            getString(R.string.fitness_water_goal_value, waterGoalLiters)
        )
        reportHealthyRangeValue.text = buildLabelLine(
            R.string.analysis_report_body_range,
            getString(R.string.fitness_weight_range_value, healthyRange.first, healthyRange.second)
        )
    }

    private fun renderWeeklyTrendChart(sessions: List<WorkoutHistorySession>) {
        reportWeeklyTrendChartContainer.removeAllViews()

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
                WorkoutNavigation.TYPE_RUNNING -> dayTotals.runningSeconds += session.durationSeconds
                WorkoutNavigation.TYPE_WALKING -> dayTotals.walkingSeconds += session.durationSeconds
                WorkoutNavigation.TYPE_CYCLING -> dayTotals.cyclingSeconds += session.durationSeconds
            }
        }

        val maxSeconds = totalsByDay.values
            .flatMap { listOf(it.runningSeconds, it.walkingSeconds, it.cyclingSeconds) }
            .maxOrNull()
            ?: 0

        reportWeeklyTrendEmptyText.visibility = if (maxSeconds == 0) View.VISIBLE else View.GONE
        reportWeeklyTrendChartContainer.visibility = if (maxSeconds == 0) View.GONE else View.VISIBLE

        if (maxSeconds == 0) {
            return
        }

        val inflater = LayoutInflater.from(this)
        days.forEachIndexed { index, day ->
            val itemView = inflater.inflate(
                R.layout.item_analysis_weekly_trend_day,
                reportWeeklyTrendChartContainer,
                false
            )
            val totals = totalsByDay[day] ?: TrendDayTotals()
            val totalSecondsForDay =
                totals.runningSeconds + totals.walkingSeconds + totals.cyclingSeconds
            val totalMinutesForDay = if (totalSecondsForDay > 0) (totalSecondsForDay + 59) / 60 else 0

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

            reportWeeklyTrendChartContainer.addView(
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
        return workoutType == WorkoutNavigation.TYPE_WALKING ||
            workoutType == WorkoutNavigation.TYPE_RUNNING ||
            workoutType == WorkoutNavigation.TYPE_CYCLING
    }

    private fun isOutdoorTrendType(workoutType: String): Boolean {
        return isOutdoorWorkout(workoutType)
    }

    private fun parseSessionDate(rawDateTime: String): LocalDate? {
        return try {
            LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    private fun isWithinLastDays(rawDateTime: String, days: Long): Boolean {
        return try {
            val parsed = LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER)
            parsed.isAfter(LocalDateTime.now().minusDays(days))
        } catch (_: Exception) {
            false
        }
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

    private fun calculateWaterGoal(weightKg: Int): Double = weightKg * 0.035

    private fun getBmiCategory(bmi: Double): Pair<Int, Int> {
        return when {
            bmi < 18.5 -> Pair(R.string.fitness_bmi_underweight, R.string.fitness_tip_underweight)
            bmi < 25.0 -> Pair(R.string.fitness_bmi_healthy, R.string.fitness_tip_healthy)
            bmi < 30.0 -> Pair(R.string.fitness_bmi_overweight, R.string.fitness_tip_overweight)
            else -> Pair(R.string.fitness_bmi_obesity, R.string.fitness_tip_obesity)
        }
    }

    private fun formatWorkoutType(rawType: String): String {
        return when (rawType) {
            WorkoutNavigation.TYPE_WEIGHT_LIFTING -> getString(R.string.fitness_activity_weight)
            WorkoutNavigation.TYPE_RUNNING -> getString(R.string.fitness_activity_running)
            WorkoutNavigation.TYPE_WALKING -> getString(R.string.fitness_activity_walking)
            WorkoutNavigation.TYPE_CYCLING -> getString(R.string.fitness_activity_cycling)
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

    private fun formatCaloriesValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun formatWeight(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun openLoginScreen(clearTask: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        if (clearTask) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startWorkout(workoutType: String) {
        startActivity(WorkoutNavigation.createIntent(this, workoutType))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_TAB, selectedTab)
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val TAB_HOME = "home"
        private const val TAB_ACTIVITIES = "activities"
        private const val TAB_HISTORY = "history"
        private const val TAB_REPORT = "report"

        private val HISTORY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    }
}

private data class ActivitySessionCard(
    val workoutType: String,
    val titleRes: Int,
    val bodyRes: Int,
    val overlayColorRes: Int,
    val imageRes: Int
)
