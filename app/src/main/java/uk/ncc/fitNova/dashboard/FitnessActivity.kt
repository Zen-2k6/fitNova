package uk.ncc.fitNova.dashboard

import android.content.res.ColorStateList
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.ncc.fitNova.R
import uk.ncc.fitNova.auth.MainActivity
import uk.ncc.fitNova.data.prefs.SessionPrefs
import uk.ncc.fitNova.data.prefs.SessionSnapshot
import uk.ncc.fitNova.data.remote.BackendConfig
import uk.ncc.fitNova.profile.ProfileActivity
import uk.ncc.fitNova.ui.applyBlackSystemBars
import uk.ncc.fitNova.workout.WorkoutHistorySession
import uk.ncc.fitNova.workout.WorkoutJsonParser
import uk.ncc.fitNova.workout.common.WorkoutNavigation
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
    private lateinit var homeTabIconBackground: View
    private lateinit var activitiesTabIconBackground: View
    private lateinit var historyTabIconBackground: View
    private lateinit var reportTabIconBackground: View
    private lateinit var homeTabIcon: ImageView
    private lateinit var activitiesTabIcon: ImageView
    private lateinit var historyTabIcon: ImageView
    private lateinit var reportTabIcon: ImageView
    private lateinit var homeTabLabel: TextView
    private lateinit var activitiesTabLabel: TextView
    private lateinit var historyTabLabel: TextView
    private lateinit var reportTabLabel: TextView

    private lateinit var historyLoadingText: TextView
    private lateinit var historyEmptyText: TextView
    private lateinit var historyContainer: LinearLayout

    private lateinit var reportLoadingText: TextView
    private lateinit var reportEmptyText: TextView
    private lateinit var reportFiltersCard: View
    private lateinit var reportOverviewCard: View
    private lateinit var reportTrendsCard: View
    private lateinit var reportBodyCard: View
    private lateinit var reportSummaryCard: View
    private lateinit var reportOverviewTitle: TextView
    private lateinit var reportChartTitle: TextView
    private lateinit var reportSelectedPeriodText: TextView
    private lateinit var reportPreviousPeriodButton: MaterialButton
    private lateinit var reportNextPeriodButton: MaterialButton
    private lateinit var reportTrendEmptyText: TextView
    private lateinit var reportTrendChartScroll: HorizontalScrollView
    private lateinit var reportTrendChartContainer: LinearLayout
    private lateinit var reportBmiValue: TextView
    private lateinit var reportWaterGoalValue: TextView
    private lateinit var reportHealthyRangeValue: TextView
    private lateinit var reportSummaryBody: TextView
    private lateinit var reportStatLabelViews: List<TextView>
    private lateinit var reportStatValueViews: List<TextView>
    private lateinit var reportActivityButtons: Map<String, MaterialButton>
    private lateinit var reportPeriodButtons: Map<DashboardAnalysisPeriod, MaterialButton>
    private lateinit var reportMetricDropdown: MaterialAutoCompleteTextView

    private var selectedTab = TAB_HOME
    private var workoutSessions: List<WorkoutHistorySession> = emptyList()
    private var hasLoadedWorkoutData = false
    private var isWorkoutDataLoading = false
    private var reportSelectedActivity = WorkoutNavigation.TYPE_RUNNING
    private var reportSelectedPeriod = DashboardAnalysisPeriod.WEEK
    private var reportSelectedMetric = DashboardAnalysisMetric.DISTANCE
    private var reportSelectedWeekOffset = 0
    private var reportSelectedMonthOffset = 0
    private val sessionPrefs by lazy { SessionPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fitness)
        applyBlackSystemBars(this)

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
        homeTabIconBackground = findViewById(R.id.bgDashboardHomeIcon)
        activitiesTabIconBackground = findViewById(R.id.bgDashboardActivitiesIcon)
        historyTabIconBackground = findViewById(R.id.bgDashboardHistoryIcon)
        reportTabIconBackground = findViewById(R.id.bgDashboardReportIcon)
        homeTabIcon = findViewById(R.id.ivDashboardHome)
        activitiesTabIcon = findViewById(R.id.ivDashboardActivities)
        historyTabIcon = findViewById(R.id.ivDashboardHistory)
        reportTabIcon = findViewById(R.id.ivDashboardReport)
        homeTabLabel = findViewById(R.id.tvDashboardHome)
        activitiesTabLabel = findViewById(R.id.tvDashboardActivities)
        historyTabLabel = findViewById(R.id.tvDashboardHistory)
        reportTabLabel = findViewById(R.id.tvDashboardReport)

        historyLoadingText = findViewById(R.id.tvDashboardHistoryLoading)
        historyEmptyText = findViewById(R.id.tvDashboardHistoryEmpty)
        historyContainer = findViewById(R.id.llDashboardHistoryItems)

        reportLoadingText = findViewById(R.id.tvDashboardReportLoading)
        reportEmptyText = findViewById(R.id.tvDashboardReportEmpty)
        reportFiltersCard = findViewById(R.id.cardDashboardAnalysisFilters)
        reportOverviewCard = findViewById(R.id.cardDashboardAnalysisOverview)
        reportTrendsCard = findViewById(R.id.cardDashboardAnalysisTrends)
        reportBodyCard = findViewById(R.id.cardDashboardAnalysisBody)
        reportSummaryCard = findViewById(R.id.cardDashboardAnalysisSummary)
        reportOverviewTitle = findViewById(R.id.tvDashboardAnalysisOverviewTitle)
        reportChartTitle = findViewById(R.id.tvDashboardAnalysisChartTitle)
        reportSelectedPeriodText = findViewById(R.id.tvDashboardAnalysisSelectedPeriod)
        reportPreviousPeriodButton = findViewById(R.id.btnDashboardAnalysisPreviousPeriod)
        reportNextPeriodButton = findViewById(R.id.btnDashboardAnalysisNextPeriod)
        reportTrendEmptyText = findViewById(R.id.tvDashboardAnalysisTrendEmpty)
        reportTrendChartScroll = findViewById(R.id.scrollDashboardAnalysisTrendChart)
        reportTrendChartContainer = findViewById(R.id.llDashboardAnalysisTrendChart)
        reportBmiValue = findViewById(R.id.tvDashboardAnalysisBmiValue)
        reportWaterGoalValue = findViewById(R.id.tvDashboardAnalysisWaterGoalValue)
        reportHealthyRangeValue = findViewById(R.id.tvDashboardAnalysisHealthyRangeValue)
        reportSummaryBody = findViewById(R.id.tvDashboardAnalysisSummaryBody)
        reportStatLabelViews = listOf(
            findViewById(R.id.tvDashboardAnalysisStatOneLabel),
            findViewById(R.id.tvDashboardAnalysisStatTwoLabel),
            findViewById(R.id.tvDashboardAnalysisStatThreeLabel),
            findViewById(R.id.tvDashboardAnalysisStatFourLabel)
        )
        reportStatValueViews = listOf(
            findViewById(R.id.tvDashboardAnalysisStatOneValue),
            findViewById(R.id.tvDashboardAnalysisStatTwoValue),
            findViewById(R.id.tvDashboardAnalysisStatThreeValue),
            findViewById(R.id.tvDashboardAnalysisStatFourValue)
        )
        reportActivityButtons = mapOf(
            WorkoutNavigation.TYPE_RUNNING to findViewById(R.id.btnDashboardAnalysisActivityRunning),
            WorkoutNavigation.TYPE_WALKING to findViewById(R.id.btnDashboardAnalysisActivityWalking),
            WorkoutNavigation.TYPE_WEIGHT_LIFTING to findViewById(R.id.btnDashboardAnalysisActivityWeight),
            WorkoutNavigation.TYPE_CYCLING to findViewById(R.id.btnDashboardAnalysisActivityCycling)
        )
        reportPeriodButtons = mapOf(
            DashboardAnalysisPeriod.WEEK to findViewById(R.id.btnDashboardAnalysisPeriodWeek),
            DashboardAnalysisPeriod.MONTH to findViewById(R.id.btnDashboardAnalysisPeriodMonth)
        )
        reportMetricDropdown = findViewById(R.id.dropdownDashboardAnalysisMetric)

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

        reportActivityButtons.forEach { (activity, button) ->
            button.setOnClickListener {
                if (reportSelectedActivity == activity) {
                    return@setOnClickListener
                }
                reportSelectedActivity = activity
                syncReportMetricSelection()
                updateReportSelectors()
                if (selectedTab == TAB_REPORT) {
                    renderReportSection(workoutSessions)
                }
            }
        }

        reportPeriodButtons.forEach { (period, button) ->
            button.setOnClickListener {
                if (reportSelectedPeriod == period) {
                    return@setOnClickListener
                }
                reportSelectedPeriod = period
                updateReportSelectors()
                if (selectedTab == TAB_REPORT) {
                    renderReportSection(workoutSessions)
                }
            }
        }

        reportMetricDropdown.setOnItemClickListener { _, _, position, _ ->
            val metrics = reportMetricsForSelectedActivity()
            val metric = metrics.getOrNull(position) ?: return@setOnItemClickListener
            if (reportSelectedMetric == metric) {
                return@setOnItemClickListener
            }
            reportSelectedMetric = metric
            updateReportSelectors()
            if (selectedTab == TAB_REPORT) {
                renderReportSection(workoutSessions)
            }
        }

        reportPreviousPeriodButton.setOnClickListener {
            when (reportSelectedPeriod) {
                DashboardAnalysisPeriod.WEEK -> reportSelectedWeekOffset += 1
                DashboardAnalysisPeriod.MONTH -> reportSelectedMonthOffset += 1
            }
            updateReportSelectors()
            if (selectedTab == TAB_REPORT) {
                renderReportSection(workoutSessions)
            }
        }

        reportNextPeriodButton.setOnClickListener {
            when (reportSelectedPeriod) {
                DashboardAnalysisPeriod.WEEK -> {
                    if (reportSelectedWeekOffset > 0) {
                        reportSelectedWeekOffset -= 1
                    }
                }

                DashboardAnalysisPeriod.MONTH -> {
                    if (reportSelectedMonthOffset > 0) {
                        reportSelectedMonthOffset -= 1
                    }
                }
            }
            updateReportSelectors()
            if (selectedTab == TAB_REPORT) {
                renderReportSection(workoutSessions)
            }
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
        val session = sessionPrefs.read()
        if (!session.isLoggedIn || session.userId <= 0) {
            Toast.makeText(this, R.string.fitness_login_required, Toast.LENGTH_SHORT).show()
            openLoginScreen(clearTask = true)
            return
        }

        val fullName = session.fullName.ifBlank {
            getString(R.string.fitness_unknown_name)
        }
        val email = session.email
        val gender = session.gender
        val age = session.age
        val weight = session.weight
        val height = session.height

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
        updateDashboardTab(
            backgroundView = homeTabIconBackground,
            iconView = homeTabIcon,
            labelView = homeTabLabel,
            isSelected = selectedTab == TAB_HOME
        )
        updateDashboardTab(
            backgroundView = activitiesTabIconBackground,
            iconView = activitiesTabIcon,
            labelView = activitiesTabLabel,
            isSelected = selectedTab == TAB_ACTIVITIES
        )
        updateDashboardTab(
            backgroundView = historyTabIconBackground,
            iconView = historyTabIcon,
            labelView = historyTabLabel,
            isSelected = selectedTab == TAB_HISTORY
        )
        updateDashboardTab(
            backgroundView = reportTabIconBackground,
            iconView = reportTabIcon,
            labelView = reportTabLabel,
            isSelected = selectedTab == TAB_REPORT
        )
    }

    private fun updateDashboardTab(
        backgroundView: View,
        iconView: ImageView,
        labelView: TextView,
        isSelected: Boolean
    ) {
        backgroundView.setBackgroundResource(
            if (isSelected) R.drawable.bg_nav_icon_selected else R.drawable.bg_nav_icon_unselected
        )
        iconView.setColorFilter(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.auth_gold_button_text else R.color.auth_hint
            )
        )
        labelView.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.auth_button_blue else R.color.auth_hint
            )
        )
        labelView.alpha = if (isSelected) 1f else 0.92f
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

        val userId = sessionPrefs.getUserId()
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
                    "limit" to "250"
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
        reportFiltersCard.visibility = View.GONE
        reportOverviewCard.visibility = View.GONE
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
            reportFiltersCard.visibility = View.GONE
            reportOverviewCard.visibility = View.GONE
            reportTrendsCard.visibility = View.GONE
            reportBodyCard.visibility = View.GONE
            reportSummaryCard.visibility = View.GONE
        }

        if (message.isNotBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun decodeSessions(array: JSONArray): List<WorkoutHistorySession> {
        return WorkoutJsonParser.decodeSessions(array)
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
        val session = sessionPrefs.read()
        val filteredSessions = sessions.filter { it.workoutType == reportSelectedActivity }

        reportLoadingText.visibility = View.GONE
        reportFiltersCard.visibility = View.VISIBLE
        reportOverviewCard.visibility = View.VISIBLE
        reportTrendsCard.visibility = View.VISIBLE
        reportBodyCard.visibility = View.VISIBLE
        reportSummaryCard.visibility = View.VISIBLE
        reportEmptyText.visibility = if (sessions.isEmpty() || filteredSessions.isEmpty()) View.VISIBLE else View.GONE
        reportEmptyText.text = when {
            sessions.isEmpty() -> getString(R.string.analysis_report_empty)
            else -> getString(
                R.string.analysis_report_empty_activity,
                formatWorkoutType(reportSelectedActivity).lowercase(Locale.getDefault())
            )
        }

        updateReportSelectors()
        renderReportOverview(filteredSessions)
        renderDashboardTrendChart(filteredSessions)
        renderReportBodySignals(session)
        reportSummaryBody.text = buildDashboardReportSummary(filteredSessions)
    }

    private fun renderReportOverview(sessions: List<WorkoutHistorySession>) {
        reportOverviewTitle.text = getString(
            R.string.analysis_report_overview_title,
            formatWorkoutType(reportSelectedActivity)
        )

        val stats = buildDashboardOverviewStats(sessions)
        stats.forEachIndexed { index, stat ->
            reportStatLabelViews[index].text = stat.label
            reportStatValueViews[index].text = stat.value
        }
    }

    private fun buildDashboardOverviewStats(sessions: List<WorkoutHistorySession>): List<DashboardAnalysisStat> {
        if (sessions.isEmpty()) {
            return reportMetricsForSelectedActivity().map { metric ->
                DashboardAnalysisStat(
                    getReportPrimaryStatLabel(metric),
                    getString(R.string.analysis_report_unknown)
                )
            }
        }

        return if (reportSelectedActivity == WorkoutNavigation.TYPE_WEIGHT_LIFTING) {
            listOf(
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_volume),
                    formatVolume(sessions.maxOfOrNull { it.totalVolume } ?: 0.0)
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_longest_time),
                    formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_reps),
                    formatCount(
                        sessions.maxOfOrNull { it.totalReps }?.toDouble() ?: 0.0,
                        R.string.analysis_report_metric_reps
                    )
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_sets),
                    formatCount(
                        sessions.maxOfOrNull { it.totalSets }?.toDouble() ?: 0.0,
                        R.string.analysis_report_metric_sets
                    )
                )
            )
        } else {
            listOf(
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_distance),
                    formatDistance(sessions.maxOfOrNull { it.distanceMeters } ?: 0.0)
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_longest_time),
                    formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0)
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_calories),
                    formatCalories(sessions.maxOfOrNull { it.caloriesBurned } ?: 0.0)
                ),
                DashboardAnalysisStat(
                    getString(R.string.analysis_report_stat_top_speed),
                    formatSpeed(sessions.maxOfOrNull { calculateAverageSpeedKph(it) } ?: 0.0)
                )
            )
        }
    }

    private fun renderDashboardTrendChart(sessions: List<WorkoutHistorySession>) {
        val chartBuckets = buildDashboardTrendBuckets(sessions)
        val maxValue = chartBuckets.maxOfOrNull { it.value } ?: 0.0
        val hasData = chartBuckets.any { it.value > 0.0 }

        reportChartTitle.text = getString(
            R.string.analysis_report_chart_title,
            formatWorkoutType(reportSelectedActivity),
            getReportMetricLabel(reportSelectedMetric)
        )
        reportSelectedPeriodText.text = getSelectedReportPeriodLabel()
        reportTrendChartContainer.removeAllViews()
        reportTrendChartScroll.visibility = if (hasData) View.VISIBLE else View.GONE
        reportTrendEmptyText.visibility = if (hasData) View.GONE else View.VISIBLE
        reportTrendEmptyText.text = getString(
            R.string.analysis_report_chart_empty,
            formatWorkoutType(reportSelectedActivity).lowercase(Locale.getDefault()),
            getReportRangeLabel().lowercase(Locale.getDefault())
        )

        if (!hasData) {
            return
        }

        val barColor = ContextCompat.getColor(this, getReportActivityColor(reportSelectedActivity))
        val inflater = LayoutInflater.from(this)
        val isDenseChart = chartBuckets.size > 10
        chartBuckets.forEachIndexed { index, bucket ->
            val itemView = inflater.inflate(
                R.layout.item_analysis_trend_bar,
                reportTrendChartContainer,
                false
            )

            val valueLabel = itemView.findViewById<TextView>(R.id.tvTrendValueLabel)
            val dayLabel = itemView.findViewById<TextView>(R.id.tvTrendDayLabel)
            valueLabel.text = formatReportMetricShort(reportSelectedMetric, bucket.value)
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

            reportTrendChartContainer.addView(
                itemView,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                    if (index < chartBuckets.lastIndex) {
                        marginEnd = if (isDenseChart) dpToPx(2) else dpToPx(6)
                    }
                }
            )
        }

        reportTrendChartScroll.post {
            reportTrendChartScroll.scrollTo(0, 0)
        }
    }

    private fun buildDashboardTrendBuckets(sessions: List<WorkoutHistorySession>): List<DashboardTrendBucket> {
        return when (reportSelectedPeriod) {
            DashboardAnalysisPeriod.WEEK -> {
                val start = currentWeekStart().minusWeeks(reportSelectedWeekOffset.toLong())
                (0..6).map { index ->
                    val day = start.plusDays(index.toLong())
                    DashboardTrendBucket(
                        label = day.format(WEEKDAY_FORMATTER),
                        value = aggregateReportMetricForDay(sessions, day)
                    )
                }
            }

            DashboardAnalysisPeriod.MONTH -> {
                val yearMonth = java.time.YearMonth.now().minusMonths(reportSelectedMonthOffset.toLong())
                (1..yearMonth.lengthOfMonth()).map { dayNumber ->
                    val day = yearMonth.atDay(dayNumber)
                    DashboardTrendBucket(
                        label = day.dayOfMonth.toString(),
                        value = aggregateReportMetricForDay(sessions, day)
                    )
                }
            }
        }
    }

    private fun aggregateReportMetricForDay(
        sessions: List<WorkoutHistorySession>,
        day: LocalDate
    ): Double {
        val daySessions = sessions.filter { parseSessionDate(it.createdAt) == day }
        if (daySessions.isEmpty()) {
            return 0.0
        }

        return when (reportSelectedMetric) {
            DashboardAnalysisMetric.DISTANCE -> daySessions.sumOf { it.distanceMeters }
            DashboardAnalysisMetric.DURATION -> daySessions.sumOf { it.durationSeconds }.toDouble()
            DashboardAnalysisMetric.CALORIES -> daySessions.sumOf { it.caloriesBurned }
            DashboardAnalysisMetric.SPEED -> daySessions.maxOfOrNull { calculateAverageSpeedKph(it) } ?: 0.0
            DashboardAnalysisMetric.VOLUME -> daySessions.sumOf { it.totalVolume }
            DashboardAnalysisMetric.REPS -> daySessions.sumOf { it.totalReps }.toDouble()
            DashboardAnalysisMetric.SETS -> daySessions.sumOf { it.totalSets }.toDouble()
        }
    }

    private fun updateReportSelectors() {
        reportActivityButtons.forEach { (activity, button) ->
            styleReportSelectorButton(button, reportSelectedActivity == activity)
        }

        reportPeriodButtons.forEach { (period, button) ->
            styleReportSelectorButton(button, reportSelectedPeriod == period)
        }

        val metrics = reportMetricsForSelectedActivity()
        val metricLabels = metrics.map(::getReportMetricLabel)
        reportMetricDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                metricLabels
            )
        )
        val selectedIndex = metrics.indexOf(reportSelectedMetric).coerceAtLeast(0)
        reportMetricDropdown.setText(metricLabels[selectedIndex], false)

        val hasNewerPeriod = when (reportSelectedPeriod) {
            DashboardAnalysisPeriod.WEEK -> reportSelectedWeekOffset > 0
            DashboardAnalysisPeriod.MONTH -> reportSelectedMonthOffset > 0
        }
        reportNextPeriodButton.isEnabled = hasNewerPeriod
        reportNextPeriodButton.alpha = if (hasNewerPeriod) 1f else 0.45f
        reportSelectedPeriodText.text = getSelectedReportPeriodLabel()
    }

    private fun styleReportSelectorButton(button: MaterialButton, isSelected: Boolean) {
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

    private fun syncReportMetricSelection() {
        val metrics = reportMetricsForSelectedActivity()
        if (reportSelectedMetric !in metrics) {
            reportSelectedMetric = metrics.first()
        }
    }

    private fun reportMetricsForSelectedActivity(): List<DashboardAnalysisMetric> {
        return if (reportSelectedActivity == WorkoutNavigation.TYPE_WEIGHT_LIFTING) {
            listOf(
                DashboardAnalysisMetric.VOLUME,
                DashboardAnalysisMetric.DURATION,
                DashboardAnalysisMetric.REPS,
                DashboardAnalysisMetric.SETS
            )
        } else {
            listOf(
                DashboardAnalysisMetric.DISTANCE,
                DashboardAnalysisMetric.DURATION,
                DashboardAnalysisMetric.CALORIES,
                DashboardAnalysisMetric.SPEED
            )
        }
    }

    private fun getReportPrimaryStatLabel(metric: DashboardAnalysisMetric): String {
        return when (metric) {
            DashboardAnalysisMetric.DISTANCE -> getString(R.string.analysis_report_stat_top_distance)
            DashboardAnalysisMetric.DURATION -> getString(R.string.analysis_report_stat_longest_time)
            DashboardAnalysisMetric.CALORIES -> getString(R.string.analysis_report_stat_top_calories)
            DashboardAnalysisMetric.SPEED -> getString(R.string.analysis_report_stat_top_speed)
            DashboardAnalysisMetric.VOLUME -> getString(R.string.analysis_report_stat_top_volume)
            DashboardAnalysisMetric.REPS -> getString(R.string.analysis_report_stat_top_reps)
            DashboardAnalysisMetric.SETS -> getString(R.string.analysis_report_stat_top_sets)
        }
    }

    private fun getReportMetricLabel(metric: DashboardAnalysisMetric): String {
        return when (metric) {
            DashboardAnalysisMetric.DISTANCE -> getString(R.string.analysis_report_metric_distance)
            DashboardAnalysisMetric.DURATION -> getString(R.string.analysis_report_metric_duration)
            DashboardAnalysisMetric.CALORIES -> getString(R.string.analysis_report_metric_calories)
            DashboardAnalysisMetric.SPEED -> getString(R.string.analysis_report_metric_speed)
            DashboardAnalysisMetric.VOLUME -> getString(R.string.analysis_report_metric_volume)
            DashboardAnalysisMetric.REPS -> getString(R.string.analysis_report_metric_reps)
            DashboardAnalysisMetric.SETS -> getString(R.string.analysis_report_metric_sets)
        }
    }

    private fun getSelectedReportPeriodLabel(): String {
        return when (reportSelectedPeriod) {
            DashboardAnalysisPeriod.WEEK -> {
                val start = currentWeekStart().minusWeeks(reportSelectedWeekOffset.toLong())
                val end = start.plusDays(6)
                getString(
                    R.string.analysis_report_period_week_label,
                    start.format(SHORT_DATE_FORMATTER),
                    end.format(SHORT_DATE_FORMATTER)
                )
            }

            DashboardAnalysisPeriod.MONTH -> {
                java.time.YearMonth.now()
                    .minusMonths(reportSelectedMonthOffset.toLong())
                    .format(MONTH_FORMATTER)
            }
        }
    }

    private fun getReportRangeLabel(): String {
        return when (reportSelectedPeriod) {
            DashboardAnalysisPeriod.WEEK -> getString(R.string.analysis_report_period_week)
            DashboardAnalysisPeriod.MONTH -> getString(R.string.analysis_report_period_month)
        }
    }

    private fun getReportActivityColor(workoutType: String): Int {
        return when (workoutType) {
            WorkoutNavigation.TYPE_RUNNING -> R.color.auth_button_blue
            WorkoutNavigation.TYPE_WALKING -> R.color.fitness_accent
            WorkoutNavigation.TYPE_WEIGHT_LIFTING -> R.color.auth_gold_yellow_dark
            WorkoutNavigation.TYPE_CYCLING -> R.color.red_brown
            else -> R.color.auth_button_blue
        }
    }

    private fun buildDashboardReportSummary(sessions: List<WorkoutHistorySession>): String {
        val activityLabel = formatWorkoutType(reportSelectedActivity)
        if (sessions.isEmpty()) {
            return getString(
                R.string.analysis_report_summary_empty_activity,
                activityLabel.lowercase(Locale.getDefault())
            )
        }

        val periodSessions = sessions.filter { session ->
            val date = parseSessionDate(session.createdAt) ?: return@filter false
            isDateInSelectedReportPeriod(date)
        }
        val periodMetricValue = aggregateReportMetricForRange(periodSessions)

        return if (reportSelectedActivity == WorkoutNavigation.TYPE_WEIGHT_LIFTING) {
            getString(
                R.string.analysis_report_summary_strength,
                activityLabel,
                sessions.size,
                formatVolume(sessions.maxOfOrNull { it.totalVolume } ?: 0.0),
                formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0),
                formatCount(
                    sessions.maxOfOrNull { it.totalReps }?.toDouble() ?: 0.0,
                    R.string.analysis_report_metric_reps
                ),
                formatCount(
                    sessions.maxOfOrNull { it.totalSets }?.toDouble() ?: 0.0,
                    R.string.analysis_report_metric_sets
                ),
                getSelectedReportPeriodLabel(),
                formatReportMetricValue(reportSelectedMetric, periodMetricValue),
                getReportMetricLabel(reportSelectedMetric).lowercase(Locale.getDefault())
            )
        } else {
            getString(
                R.string.analysis_report_summary_outdoor,
                activityLabel,
                sessions.size,
                formatDistance(sessions.maxOfOrNull { it.distanceMeters } ?: 0.0),
                formatDuration(sessions.maxOfOrNull { it.durationSeconds } ?: 0),
                formatCalories(sessions.maxOfOrNull { it.caloriesBurned } ?: 0.0),
                formatSpeed(sessions.maxOfOrNull { calculateAverageSpeedKph(it) } ?: 0.0),
                getSelectedReportPeriodLabel(),
                formatReportMetricValue(reportSelectedMetric, periodMetricValue),
                getReportMetricLabel(reportSelectedMetric).lowercase(Locale.getDefault())
            )
        }
    }

    private fun isDateInSelectedReportPeriod(date: LocalDate): Boolean {
        return when (reportSelectedPeriod) {
            DashboardAnalysisPeriod.WEEK -> {
                val start = currentWeekStart().minusWeeks(reportSelectedWeekOffset.toLong())
                val end = start.plusDays(6)
                !date.isBefore(start) && !date.isAfter(end)
            }

            DashboardAnalysisPeriod.MONTH -> {
                java.time.YearMonth.from(date) ==
                    java.time.YearMonth.now().minusMonths(reportSelectedMonthOffset.toLong())
            }
        }
    }

    private fun aggregateReportMetricForRange(sessions: List<WorkoutHistorySession>): Double {
        if (sessions.isEmpty()) {
            return 0.0
        }

        return when (reportSelectedMetric) {
            DashboardAnalysisMetric.DISTANCE -> sessions.sumOf { it.distanceMeters }
            DashboardAnalysisMetric.DURATION -> sessions.sumOf { it.durationSeconds }.toDouble()
            DashboardAnalysisMetric.CALORIES -> sessions.sumOf { it.caloriesBurned }
            DashboardAnalysisMetric.SPEED -> sessions.maxOfOrNull { calculateAverageSpeedKph(it) } ?: 0.0
            DashboardAnalysisMetric.VOLUME -> sessions.sumOf { it.totalVolume }
            DashboardAnalysisMetric.REPS -> sessions.sumOf { it.totalReps }.toDouble()
            DashboardAnalysisMetric.SETS -> sessions.sumOf { it.totalSets }.toDouble()
        }
    }

    private fun formatReportMetricValue(metric: DashboardAnalysisMetric, value: Double): String {
        return when (metric) {
            DashboardAnalysisMetric.DISTANCE -> formatDistance(value)
            DashboardAnalysisMetric.DURATION -> formatDuration(value.toInt())
            DashboardAnalysisMetric.CALORIES -> formatCalories(value)
            DashboardAnalysisMetric.SPEED -> formatSpeed(value)
            DashboardAnalysisMetric.VOLUME -> formatVolume(value)
            DashboardAnalysisMetric.REPS -> formatCount(value, R.string.analysis_report_metric_reps)
            DashboardAnalysisMetric.SETS -> formatCount(value, R.string.analysis_report_metric_sets)
        }
    }

    private fun formatReportMetricShort(metric: DashboardAnalysisMetric, value: Double): String {
        return when (metric) {
            DashboardAnalysisMetric.DISTANCE -> if (value >= 1000.0) {
                getString(R.string.analysis_report_chart_bar_distance_km, value / 1000.0)
            } else {
                getString(R.string.analysis_report_chart_bar_distance_m, value)
            }

            DashboardAnalysisMetric.DURATION -> {
                val totalMinutes = (value / 60.0).toInt()
                if (totalMinutes >= 60) {
                    getString(R.string.analysis_report_chart_bar_duration_hours, totalMinutes / 60)
                } else {
                    getString(R.string.analysis_report_chart_bar_duration_minutes, totalMinutes)
                }
            }

            DashboardAnalysisMetric.CALORIES -> getString(R.string.analysis_report_chart_bar_calories, value)
            DashboardAnalysisMetric.SPEED -> getString(R.string.analysis_report_chart_bar_speed, value)
            DashboardAnalysisMetric.VOLUME -> getString(R.string.analysis_report_chart_bar_volume, value)
            DashboardAnalysisMetric.REPS,
            DashboardAnalysisMetric.SETS -> getString(R.string.analysis_report_chart_bar_count, value)
        }
    }

    private fun renderReportBodySignals(session: SessionSnapshot) {
        val weight = session.weight
        val height = session.height

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

    private fun buildLabelLine(labelRes: Int, value: String): String {
        return "${getString(labelRes)}: $value"
    }

    private fun currentWeekStart(): LocalDate {
        return LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
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

    private fun formatSpeed(speedKph: Double): String {
        return String.format(Locale.getDefault(), "%.1f km/h", speedKph)
    }

    private fun formatVolume(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f kg", value)
    }

    private fun formatCount(value: Double, labelRes: Int): String {
        return getString(
            R.string.analysis_report_count_value,
            value.toInt(),
            getString(labelRes).lowercase(Locale.getDefault())
        )
    }

    private fun calculateAverageSpeedKph(session: WorkoutHistorySession): Double {
        if (session.durationSeconds <= 0 || session.distanceMeters <= 0.0) {
            return 0.0
        }
        return (session.distanceMeters / session.durationSeconds) * 3.6
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
        private val SHORT_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        private val MONTH_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
}

private enum class DashboardAnalysisPeriod {
    WEEK,
    MONTH
}

private enum class DashboardAnalysisMetric {
    DISTANCE,
    DURATION,
    CALORIES,
    SPEED,
    VOLUME,
    REPS,
    SETS
}

private data class DashboardAnalysisStat(
    val label: String,
    val value: String
)

private data class DashboardTrendBucket(
    val label: String,
    val value: Double
)

private data class ActivitySessionCard(
    val workoutType: String,
    val titleRes: Int,
    val bodyRes: Int,
    val overlayColorRes: Int,
    val imageRes: Int
)
