package uk.ncc.fitNova.workout.outdoor

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.ncc.fitNova.R
import uk.ncc.fitNova.data.prefs.SessionPrefs
import uk.ncc.fitNova.data.remote.BackendConfig
import uk.ncc.fitNova.ui.applyBlackSystemBars
import uk.ncc.fitNova.workout.WorkoutHistorySession
import uk.ncc.fitNova.workout.WorkoutJsonParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OutdoorWorkoutActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var mapContainerView: View
    private lateinit var outdoorPanelCard: MaterialCardView
    private lateinit var durationValueText: TextView
    private lateinit var distanceValueText: TextView
    private lateinit var speedValueText: TextView
    private lateinit var caloriesValueText: TextView
    private lateinit var routeNameValueText: TextView
    private lateinit var destinationValueText: TextView
    private lateinit var remainingValueText: TextView
    private lateinit var targetDurationValueText: TextView
    private lateinit var toggleButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var setTargetDurationButton: MaterialButton
    private lateinit var targetDurationProgress: LinearProgressIndicator
    private lateinit var trackPanelLayout: LinearLayout
    private lateinit var dataPanelLayout: LinearLayout
    private lateinit var logPanelLayout: LinearLayout
    private lateinit var panelTrackTabView: View
    private lateinit var panelLogsTabView: View
    private lateinit var panelDataTabView: View
    private lateinit var panelTrackIconBackground: View
    private lateinit var panelLogsIconBackground: View
    private lateinit var panelDataIconBackground: View
    private lateinit var panelTrackIcon: ImageView
    private lateinit var panelLogsIcon: ImageView
    private lateinit var panelDataIcon: ImageView
    private lateinit var panelTrackLabel: TextView
    private lateinit var panelLogsLabel: TextView
    private lateinit var panelDataLabel: TextView
    private lateinit var logTitleText: TextView
    private lateinit var logLoadingText: TextView
    private lateinit var logEmptyText: TextView
    private lateinit var logTableScroll: HorizontalScrollView
    private lateinit var logTableRowsContainer: LinearLayout
    private lateinit var monthlyGoalTitleText: TextView
    private lateinit var monthlyGoalMonthText: TextView
    private lateinit var monthlyDistanceGoalInput: TextInputEditText
    private lateinit var monthlyCaloriesGoalInput: TextInputEditText
    private lateinit var saveMonthlyGoalButton: MaterialButton
    private lateinit var monthlyGoalSummaryText: TextView
    private lateinit var dataMonitorTitleText: TextView
    private lateinit var distanceGoalPercentText: TextView
    private lateinit var distanceGoalValueText: TextView
    private lateinit var distanceGoalStatusText: TextView
    private lateinit var distanceGoalProgress: LinearProgressIndicator
    private lateinit var setDistanceGoalButton: MaterialButton
    private lateinit var clearDistanceGoalButton: MaterialButton
    private lateinit var caloriesGoalPercentText: TextView
    private lateinit var caloriesGoalValueText: TextView
    private lateinit var caloriesGoalStatusText: TextView
    private lateinit var caloriesGoalProgress: LinearProgressIndicator
    private lateinit var setCaloriesGoalButton: MaterialButton
    private lateinit var clearCaloriesGoalButton: MaterialButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var googleMap: GoogleMap
    private var routePolyline: Polyline? = null
    private var destinationMarker: Marker? = null
    private var isMapReady = false

    private var workoutType = WORKOUT_TYPE_WALKING
    private var isTracking = false
    private var isSaving = false
    private var secondsElapsed = 0
    private var totalDistanceMeters = 0.0
    private var currentSpeedMetersPerSecond = 0.0
    private var caloriesBurned = 0.0
    private var hasCenteredMap = false
    private var hasArrivedAtDestination = false
    private var hasReachedTargetDuration = false
    private var hasReachedDistanceGoal = false
    private var hasReachedCaloriesGoal = false
    private var lastLocation: Location? = null
    private var destinationPoint: LatLng? = null
    private var routeName = ""
    private var targetDurationSeconds = 0
    private var distanceGoalMeters = 0.0
    private var caloriesGoal = 0.0
    private var selectedPanel = PANEL_TRACK
    private var hasLoadedLog = false
    private var isLogLoading = false
    private val routePoints = arrayListOf<LatLng>()
    private val outdoorLogSessions = mutableListOf<WorkoutHistorySession>()
    private val sessionPrefs by lazy { SessionPrefs(this) }

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) {
                return
            }

            secondsElapsed++
            updateTargetDurationState(showToast = true)
            renderMetrics()
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableUserLocationLayer()
            startTracking()
        } else {
            Toast.makeText(
                this,
                R.string.outdoor_workout_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_outdoor_workout)
        applyBlackSystemBars(this)
        applySystemBarInsets(findViewById(R.id.main))

        workoutType = savedInstanceState?.getString(KEY_WORKOUT_TYPE)
            ?: intent.getStringExtra(EXTRA_WORKOUT_TYPE)?.lowercase(Locale.US)
            ?: WORKOUT_TYPE_WALKING
        selectedPanel = savedInstanceState?.getString(KEY_SELECTED_PANEL) ?: PANEL_TRACK

        bindViews()
        setupLocationTracking()
        bindActions()
        loadMonthlyGoal()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            renderMetrics()
            updateToggleButton()
        }
        showSelectedPanel(loadLogIfNeeded = selectedPanel == PANEL_LOGS)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.main)
        mapContainerView = findViewById(R.id.map)
        outdoorPanelCard = findViewById(R.id.cardOutdoorPanel)
        durationValueText = findViewById(R.id.tvOutdoorDurationValue)
        distanceValueText = findViewById(R.id.tvOutdoorDistanceValue)
        speedValueText = findViewById(R.id.tvOutdoorSpeedValue)
        caloriesValueText = findViewById(R.id.tvOutdoorCaloriesValue)
        routeNameValueText = findViewById(R.id.tvOutdoorRouteNameValue)
        destinationValueText = findViewById(R.id.tvOutdoorDestinationValue)
        remainingValueText = findViewById(R.id.tvOutdoorRemainingValue)
        targetDurationValueText = findViewById(R.id.tvOutdoorTargetDurationValue)
        toggleButton = findViewById(R.id.btnToggleOutdoorWorkout)
        finishButton = findViewById(R.id.btnFinishOutdoorWorkout)
        setTargetDurationButton = findViewById(R.id.btnSetOutdoorDuration)
        targetDurationProgress = findViewById(R.id.progressOutdoorTargetDuration)
        trackPanelLayout = findViewById(R.id.layoutOutdoorTrackPanel)
        dataPanelLayout = findViewById(R.id.layoutOutdoorDataPanel)
        logPanelLayout = findViewById(R.id.layoutOutdoorLogPanel)
        panelTrackTabView = findViewById(R.id.tabOutdoorTrack)
        panelLogsTabView = findViewById(R.id.tabOutdoorLogs)
        panelDataTabView = findViewById(R.id.tabOutdoorData)
        panelTrackIconBackground = findViewById(R.id.bgOutdoorTrackIcon)
        panelLogsIconBackground = findViewById(R.id.bgOutdoorLogsIcon)
        panelDataIconBackground = findViewById(R.id.bgOutdoorDataIcon)
        panelTrackIcon = findViewById(R.id.ivOutdoorTrackIcon)
        panelLogsIcon = findViewById(R.id.ivOutdoorLogsIcon)
        panelDataIcon = findViewById(R.id.ivOutdoorDataIcon)
        panelTrackLabel = findViewById(R.id.tvOutdoorTrackLabel)
        panelLogsLabel = findViewById(R.id.tvOutdoorLogsLabel)
        panelDataLabel = findViewById(R.id.tvOutdoorDataLabel)
        logTitleText = findViewById(R.id.tvOutdoorLogTitle)
        logLoadingText = findViewById(R.id.tvOutdoorLogLoading)
        logEmptyText = findViewById(R.id.tvOutdoorLogEmpty)
        logTableScroll = findViewById(R.id.scrollOutdoorLogTable)
        logTableRowsContainer = findViewById(R.id.llOutdoorLogTableRows)
        monthlyGoalTitleText = findViewById(R.id.tvOutdoorMonthlyGoalTitle)
        monthlyGoalMonthText = findViewById(R.id.tvOutdoorMonthlyGoalMonth)
        monthlyDistanceGoalInput = findViewById(R.id.etOutdoorMonthlyDistanceGoal)
        monthlyCaloriesGoalInput = findViewById(R.id.etOutdoorMonthlyCaloriesGoal)
        saveMonthlyGoalButton = findViewById(R.id.btnSaveOutdoorMonthlyGoal)
        monthlyGoalSummaryText = findViewById(R.id.tvOutdoorMonthlyGoalSummary)
        dataMonitorTitleText = findViewById(R.id.tvOutdoorDataMonitorTitle)
        distanceGoalPercentText = findViewById(R.id.tvOutdoorDistanceGoalPercent)
        distanceGoalValueText = findViewById(R.id.tvOutdoorDistanceGoalValue)
        distanceGoalStatusText = findViewById(R.id.tvOutdoorDistanceGoalStatus)
        distanceGoalProgress = findViewById(R.id.progressOutdoorDistanceGoal)
        setDistanceGoalButton = findViewById(R.id.btnSetOutdoorDistanceGoal)
        clearDistanceGoalButton = findViewById(R.id.btnClearOutdoorDistanceGoal)
        caloriesGoalPercentText = findViewById(R.id.tvOutdoorCaloriesGoalPercent)
        caloriesGoalValueText = findViewById(R.id.tvOutdoorCaloriesGoalValue)
        caloriesGoalStatusText = findViewById(R.id.tvOutdoorCaloriesGoalStatus)
        caloriesGoalProgress = findViewById(R.id.progressOutdoorCaloriesGoal)
        setCaloriesGoalButton = findViewById(R.id.btnSetOutdoorCaloriesGoal)
        clearCaloriesGoalButton = findViewById(R.id.btnClearOutdoorCaloriesGoal)

        logTitleText.text = getString(
            R.string.outdoor_workout_logs_title,
            getWorkoutTypeLabel()
        )
        monthlyGoalTitleText.text = getString(
            R.string.outdoor_workout_monthly_goal_title,
            getWorkoutTypeLabel()
        )
        monthlyGoalMonthText.text = getString(
            R.string.outdoor_workout_monthly_goal_month,
            getCurrentMonthDisplay()
        )
        saveMonthlyGoalButton.text = getString(
            R.string.outdoor_workout_monthly_goal_save,
            getWorkoutTypeLabel()
        )
        dataMonitorTitleText.text = getString(
            R.string.outdoor_workout_data_monitor_title,
            getWorkoutTypeLabel()
        )
        panelTrackLabel.text = getString(
            R.string.outdoor_workout_tab_track,
            getWorkoutTypeTabLabel()
        )
        panelLogsLabel.text = getString(
            R.string.outdoor_workout_tab_logs,
            getWorkoutTypeTabLabel()
        )
        panelDataLabel.text = getString(
            R.string.outdoor_workout_tab_data,
            getWorkoutTypeTabLabel()
        )
    }

    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(3f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleLocationUpdate(location)
            }
        }
    }

    private fun bindActions() {
        toggleButton.setOnClickListener {
            if (isTracking) {
                pauseTracking()
            } else {
                startTracking()
            }
        }
        finishButton.setOnClickListener { saveSessionAndFinish() }
        setTargetDurationButton.setOnClickListener { showTargetDurationDialog() }
        setDistanceGoalButton.setOnClickListener { showDistanceGoalDialog() }
        clearDistanceGoalButton.setOnClickListener { clearDistanceGoal() }
        setCaloriesGoalButton.setOnClickListener { showCaloriesGoalDialog() }
        clearCaloriesGoalButton.setOnClickListener { clearCaloriesGoal() }
        saveMonthlyGoalButton.setOnClickListener { saveMonthlyGoal() }
        panelTrackTabView.setOnClickListener {
            selectedPanel = PANEL_TRACK
            showSelectedPanel()
        }
        panelLogsTabView.setOnClickListener {
            selectedPanel = PANEL_LOGS
            showSelectedPanel()
        }
        panelDataTabView.setOnClickListener {
            selectedPanel = PANEL_DATA
            showSelectedPanel()
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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.setOnMapLongClickListener { point ->
            setDestination(point)
        }
        googleMap.setOnMarkerClickListener { marker ->
            marker == destinationMarker
        }

        if (hasLocationPermission()) {
            enableUserLocationLayer()
        }

        redrawDestinationMarker()

        if (routePoints.isNotEmpty()) {
            redrawRoute()
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.last(), 16f))
            hasCenteredMap = true
        }
    }

    private fun startTracking() {
        if (isSaving) {
            return
        }

        if (!hasLocationPermission()) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        lastLocation = null
        currentSpeedMetersPerSecond = 0.0
        isTracking = true
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000)
        startLocationUpdates()
        renderMetrics()
        updateToggleButton()
    }

    private fun pauseTracking() {
        isTracking = false
        currentSpeedMetersPerSecond = 0.0
        timerHandler.removeCallbacks(timerRunnable)
        stopLocationUpdates()
        renderMetrics()
        updateToggleButton()
    }

    private fun updateToggleButton() {
        val labelRes = when {
            isTracking -> R.string.outdoor_workout_pause
            secondsElapsed > 0 || totalDistanceMeters > 0.0 -> R.string.outdoor_workout_resume
            else -> R.string.outdoor_workout_start
        }
        val fillColorRes = if (isTracking) R.color.neon_button_red_fill else R.color.neon_button_fill
        val strokeColorRes = if (isTracking) R.color.neon_red_glow else R.color.neon_blue_glow

        toggleButton.text = getString(labelRes)
        toggleButton.setTextColor(ContextCompat.getColor(this, R.color.auth_gold_button_text))
        toggleButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, fillColorRes))
        toggleButton.strokeColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, strokeColorRes))
        toggleButton.rippleColor =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (isTracking) R.color.neon_red_glow_soft else R.color.neon_blue_glow_soft
                )
            )
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (securityException: SecurityException) {
            Log.e("OutdoorWorkout", "Location permission lost: ${securityException.message}")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleLocationUpdate(location: Location) {
        if (!isTracking || location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return
        }

        val point = LatLng(location.latitude, location.longitude)
        currentSpeedMetersPerSecond = location.speed.toDouble().coerceAtLeast(0.0)

        if (routePoints.isEmpty()) {
            routePoints.add(point)
            lastLocation = location
            redrawRoute()
            updateDestinationState(location)
            renderMetrics()
            centerMap(point)
            return
        }

        val previousLocation = lastLocation
        lastLocation = location

        if (previousLocation == null) {
            routePoints.add(point)
            redrawRoute()
            updateDestinationState(location)
            renderMetrics()
            return
        }

        val segmentDistance = previousLocation.distanceTo(location).toDouble()
        val segmentDurationSeconds = ((location.time - previousLocation.time).toDouble() / 1000.0)
            .coerceAtLeast(0.0)
        if (
            segmentDistance < MIN_TRACKABLE_SEGMENT_METERS ||
            segmentDistance > MAX_TRACKABLE_SEGMENT_METERS
        ) {
            if (currentSpeedMetersPerSecond <= 0.0 && segmentDurationSeconds > 0.0) {
                currentSpeedMetersPerSecond = segmentDistance / segmentDurationSeconds
            }
            renderMetrics()
            return
        }

        if (currentSpeedMetersPerSecond <= 0.0 && segmentDurationSeconds > 0.0) {
            currentSpeedMetersPerSecond = segmentDistance / segmentDurationSeconds
        }
        totalDistanceMeters += segmentDistance
        routePoints.add(point)
        recalculateCalories()
        updateGoalProgressState(showToast = true)
        renderMetrics()
        redrawRoute()
        updateDestinationState(location)

        if (!hasCenteredMap) {
            centerMap(point)
        }
    }

    private fun centerMap(point: LatLng) {
        if (!isMapReady) {
            return
        }

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
        hasCenteredMap = true
    }

    private fun redrawRoute() {
        if (!isMapReady) {
            return
        }

        routePolyline?.remove()
        routePolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(ContextCompat.getColor(this, R.color.auth_button_blue))
                .width(10f)
        )
    }

    private fun enableUserLocationLayer() {
        if (!isMapReady || !hasLocationPermission()) {
            return
        }

        try {
            googleMap.isMyLocationEnabled = true
        } catch (securityException: SecurityException) {
            Log.e("OutdoorWorkout", "Failed to enable user location: ${securityException.message}")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderMetrics() {
        durationValueText.text = formatDuration(secondsElapsed)
        distanceValueText.text = formatDistance(totalDistanceMeters)
        speedValueText.text = formatSpeed(currentSpeedMetersPerSecond)
        caloriesValueText.text = formatCalories(caloriesBurned)
        renderTargetDurationInfo()
        renderDestinationInfo()
        renderGoalMonitoring()
    }

    private fun recalculateCalories() {
        val weightKg = sessionPrefs.getWeight()
        val distanceKm = totalDistanceMeters / 1000.0
        val multiplier = when (workoutType) {
            WORKOUT_TYPE_RUNNING -> 1.0
            WORKOUT_TYPE_CYCLING -> 0.6
            else -> 0.75
        }

        caloriesBurned = if (weightKg > 0) {
            distanceKm * weightKg * multiplier
        } else {
            0.0
        }
    }

    private fun saveSessionAndFinish() {
        if (isSaving) {
            return
        }

        if (secondsElapsed <= 0 && totalDistanceMeters <= 0.0) {
            Toast.makeText(this, R.string.outdoor_workout_session_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userId = sessionPrefs.getUserId()
        if (userId <= 0) {
            Toast.makeText(this, R.string.outdoor_workout_save_user_missing, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val destinationLat = destinationPoint?.latitude
        val destinationLng = destinationPoint?.longitude
        val remainingDistanceMeters = getCurrentRemainingDistance()
        val routeJson = buildRouteJson()

        pauseTracking()

        isSaving = true
        toggleButton.isEnabled = false
        finishButton.isEnabled = false
        setTargetDurationButton.isEnabled = false
        setDistanceGoalButton.isEnabled = false
        clearDistanceGoalButton.isEnabled = false
        setCaloriesGoalButton.isEnabled = false
        clearCaloriesGoalButton.isEnabled = false
        finishButton.text = getString(R.string.weight_lifting_saving)

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") == "true") {
                        Toast.makeText(
                            this,
                            R.string.outdoor_workout_save_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        resetSaveState()
                        val message = payload.optString(
                            "message",
                            getString(R.string.outdoor_workout_save_failed)
                        )
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                } catch (exception: JSONException) {
                    Log.e("OutdoorWorkout", "Invalid save response: ${exception.message}")
                    resetSaveState()
                    Toast.makeText(
                        this,
                        R.string.outdoor_workout_save_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("OutdoorWorkout", "Failed to save outdoor workout: $error")
                resetSaveState()
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_save_network_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "saveWorkoutSession",
                    "userId" to userId.toString(),
                    "workoutType" to workoutType,
                    "durationSeconds" to secondsElapsed.toString(),
                    "totalSets" to "0",
                    "totalReps" to "0",
                    "totalVolume" to "0.00",
                    "distanceMeters" to String.format(Locale.US, "%.2f", totalDistanceMeters),
                    "caloriesBurned" to String.format(Locale.US, "%.2f", caloriesBurned),
                    "routeName" to routeName,
                    "destinationLat" to destinationLat?.let {
                        String.format(Locale.US, "%.8f", it)
                    }.orEmpty(),
                    "destinationLng" to destinationLng?.let {
                        String.format(Locale.US, "%.8f", it)
                    }.orEmpty(),
                    "remainingDistanceMeters" to String.format(
                        Locale.US,
                        "%.2f",
                        remainingDistanceMeters
                    ),
                    "destinationReached" to if (hasArrivedAtDestination) "1" else "0",
                    "targetDurationSeconds" to targetDurationSeconds.toString(),
                    "targetDurationReached" to if (hasReachedTargetDuration) "1" else "0",
                    "distanceGoalMeters" to String.format(Locale.US, "%.2f", distanceGoalMeters),
                    "caloriesGoal" to String.format(Locale.US, "%.2f", caloriesGoal),
                    "routeJson" to routeJson,
                    "setLogJson" to "[]"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun resetSaveState() {
        isSaving = false
        toggleButton.isEnabled = true
        finishButton.isEnabled = true
        setTargetDurationButton.isEnabled = true
        setDistanceGoalButton.isEnabled = true
        clearDistanceGoalButton.isEnabled = true
        setCaloriesGoalButton.isEnabled = true
        clearCaloriesGoalButton.isEnabled = true
        finishButton.text = getString(R.string.outdoor_workout_finish)
        updateToggleButton()
    }

    private fun restoreState(bundle: Bundle) {
        isTracking = bundle.getBoolean(KEY_IS_TRACKING)
        isSaving = bundle.getBoolean(KEY_IS_SAVING)
        secondsElapsed = bundle.getInt(KEY_SECONDS_ELAPSED)
        totalDistanceMeters = bundle.getDouble(KEY_TOTAL_DISTANCE)
        currentSpeedMetersPerSecond = bundle.getDouble(KEY_CURRENT_SPEED)
        caloriesBurned = bundle.getDouble(KEY_CALORIES_BURNED)
        hasArrivedAtDestination = bundle.getBoolean(KEY_HAS_ARRIVED_AT_DESTINATION)
        hasReachedTargetDuration = bundle.getBoolean(KEY_HAS_REACHED_TARGET_DURATION)
        hasReachedDistanceGoal = bundle.getBoolean(KEY_HAS_REACHED_DISTANCE_GOAL)
        hasReachedCaloriesGoal = bundle.getBoolean(KEY_HAS_REACHED_CALORIES_GOAL)
        targetDurationSeconds = bundle.getInt(KEY_TARGET_DURATION_SECONDS)
        distanceGoalMeters = bundle.getDouble(KEY_DISTANCE_GOAL_METERS)
        caloriesGoal = bundle.getDouble(KEY_CALORIES_GOAL)
        routeName = bundle.getString(KEY_ROUTE_NAME).orEmpty()
        destinationPoint = bundle.getParcelable(KEY_DESTINATION_POINT)
        routePoints.clear()
        routePoints.addAll(
            bundle.getParcelableArrayList<LatLng>(KEY_ROUTE_POINTS) ?: arrayListOf()
        )

        lastLocation = routePoints.lastOrNull()?.let { lastPoint ->
            Location("saved").apply {
                latitude = lastPoint.latitude
                longitude = lastPoint.longitude
            }
        }

        renderMetrics()
        updateToggleButton()
        redrawDestinationMarker()
        updateGoalProgressState(showToast = false)

        if (isSaving) {
            toggleButton.isEnabled = false
            finishButton.isEnabled = false
            setTargetDurationButton.isEnabled = false
            setDistanceGoalButton.isEnabled = false
            clearDistanceGoalButton.isEnabled = false
            setCaloriesGoalButton.isEnabled = false
            clearCaloriesGoalButton.isEnabled = false
            finishButton.text = getString(R.string.weight_lifting_saving)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isTracking) {
            timerHandler.removeCallbacks(timerRunnable)
            timerHandler.postDelayed(timerRunnable, 1000)
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        timerHandler.removeCallbacks(timerRunnable)
        stopLocationUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_WORKOUT_TYPE, workoutType)
        outState.putBoolean(KEY_IS_TRACKING, isTracking)
        outState.putBoolean(KEY_IS_SAVING, isSaving)
        outState.putInt(KEY_SECONDS_ELAPSED, secondsElapsed)
        outState.putDouble(KEY_TOTAL_DISTANCE, totalDistanceMeters)
        outState.putDouble(KEY_CURRENT_SPEED, currentSpeedMetersPerSecond)
        outState.putDouble(KEY_CALORIES_BURNED, caloriesBurned)
        outState.putBoolean(KEY_HAS_ARRIVED_AT_DESTINATION, hasArrivedAtDestination)
        outState.putBoolean(KEY_HAS_REACHED_TARGET_DURATION, hasReachedTargetDuration)
        outState.putBoolean(KEY_HAS_REACHED_DISTANCE_GOAL, hasReachedDistanceGoal)
        outState.putBoolean(KEY_HAS_REACHED_CALORIES_GOAL, hasReachedCaloriesGoal)
        outState.putInt(KEY_TARGET_DURATION_SECONDS, targetDurationSeconds)
        outState.putDouble(KEY_DISTANCE_GOAL_METERS, distanceGoalMeters)
        outState.putDouble(KEY_CALORIES_GOAL, caloriesGoal)
        outState.putString(KEY_ROUTE_NAME, routeName)
        outState.putString(KEY_SELECTED_PANEL, selectedPanel)
        outState.putParcelable(KEY_DESTINATION_POINT, destinationPoint)
        outState.putParcelableArrayList(KEY_ROUTE_POINTS, ArrayList(routePoints))
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        stopLocationUpdates()
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

    private fun formatSpeed(speedMetersPerSecond: Double): String {
        val speedKilometersPerHour = speedMetersPerSecond * 3.6
        return String.format(Locale.getDefault(), "%.1f km/h", speedKilometersPerHour)
    }

    private fun formatCalories(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f kcal", value)
    }

    private fun formatCaloriesValue(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f", value)
    }

    private fun showSelectedPanel(loadLogIfNeeded: Boolean = true) {
        updateMapVisibilityForSelectedPanel()
        trackPanelLayout.visibility = if (selectedPanel == PANEL_TRACK) View.VISIBLE else View.GONE
        logPanelLayout.visibility = if (selectedPanel == PANEL_LOGS) View.VISIBLE else View.GONE
        dataPanelLayout.visibility = if (selectedPanel == PANEL_DATA) View.VISIBLE else View.GONE
        renderPanelTabs()

        if (selectedPanel == PANEL_LOGS) {
            if (hasLoadedLog) {
                renderLogSessions()
            } else if (loadLogIfNeeded) {
                loadOutdoorLog()
            }
        }
    }

    private fun updateMapVisibilityForSelectedPanel() {
        val shouldShowMap = selectedPanel == PANEL_TRACK
        mapContainerView.visibility = if (shouldShowMap) View.VISIBLE else View.GONE

        val params = outdoorPanelCard.layoutParams as ConstraintLayout.LayoutParams
        if (shouldShowMap) {
            params.topToTop = R.id.guidelineOutdoorSplit
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topMargin = 0
        } else {
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topMargin = dpToPx(12)
        }
        outdoorPanelCard.layoutParams = params
        rootLayout.requestLayout()
    }

    private fun renderPanelTabs() {
        updatePanelTab(
            backgroundView = panelTrackIconBackground,
            iconView = panelTrackIcon,
            labelView = panelTrackLabel,
            isSelected = selectedPanel == PANEL_TRACK
        )
        updatePanelTab(
            backgroundView = panelLogsIconBackground,
            iconView = panelLogsIcon,
            labelView = panelLogsLabel,
            isSelected = selectedPanel == PANEL_LOGS
        )
        updatePanelTab(
            backgroundView = panelDataIconBackground,
            iconView = panelDataIcon,
            labelView = panelDataLabel,
            isSelected = selectedPanel == PANEL_DATA
        )
    }

    private fun updatePanelTab(
        backgroundView: View,
        iconView: ImageView,
        labelView: TextView,
        isSelected: Boolean
    ) {
        backgroundView.setBackgroundResource(
            if (isSelected) R.drawable.bg_nav_icon_selected else R.drawable.bg_nav_icon_unselected
        )
        val iconColor = ContextCompat.getColor(
            this,
            if (isSelected) R.color.auth_gold_button_text else R.color.auth_hint
        )
        iconView.setColorFilter(iconColor)
        labelView.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.auth_button_blue else R.color.auth_hint
            )
        )
        labelView.alpha = if (isSelected) 1f else 0.92f
    }

    private fun getWorkoutTypeLabel(): String {
        return when (workoutType) {
            WORKOUT_TYPE_RUNNING -> getString(R.string.fitness_activity_running)
            WORKOUT_TYPE_CYCLING -> getString(R.string.fitness_activity_cycling)
            else -> getString(R.string.fitness_activity_walking)
        }
    }

    private fun getWorkoutTypeTabLabel(): String {
        return when (workoutType) {
            WORKOUT_TYPE_RUNNING -> getString(R.string.outdoor_workout_nav_run)
            WORKOUT_TYPE_CYCLING -> getString(R.string.outdoor_workout_nav_cycle)
            else -> getString(R.string.outdoor_workout_nav_walk)
        }
    }

    private fun loadOutdoorLog() {
        if (isLogLoading) {
            return
        }

        val userId = sessionPrefs.getUserId()
        if (userId <= 0) {
            Toast.makeText(this, R.string.outdoor_workout_save_user_missing, Toast.LENGTH_SHORT).show()
            return
        }

        isLogLoading = true
        showLogLoadingState()

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                isLogLoading = false
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        showLogErrorState(
                            payload.optString("message", getString(R.string.workout_history_load_failed))
                        )
                        return@Listener
                    }

                    val sessions = decodeOutdoorLogSessions(payload.optJSONArray("sessions") ?: JSONArray())
                    outdoorLogSessions.clear()
                    outdoorLogSessions.addAll(sessions.filter { it.workoutType == workoutType })
                    hasLoadedLog = true
                    renderLogSessions()
                } catch (_: JSONException) {
                    showLogErrorState(getString(R.string.workout_history_load_failed))
                }
            },
            Response.ErrorListener {
                isLogLoading = false
                showLogErrorState(getString(R.string.workout_history_load_network_error))
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

    private fun showLogLoadingState() {
        logLoadingText.text = getString(
            R.string.outdoor_workout_log_loading,
            getWorkoutTypeLabel().lowercase(Locale.getDefault())
        )
        logLoadingText.visibility = View.VISIBLE
        logEmptyText.visibility = View.GONE
        logTableScroll.visibility = View.GONE
        logTableRowsContainer.removeAllViews()
    }

    private fun showLogErrorState(message: String) {
        logLoadingText.visibility = View.GONE
        logTableScroll.visibility = View.GONE
        logTableRowsContainer.removeAllViews()
        logEmptyText.text = message
        logEmptyText.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun renderLogSessions() {
        logLoadingText.visibility = View.GONE
        logTableRowsContainer.removeAllViews()

        if (outdoorLogSessions.isEmpty()) {
            logTableScroll.visibility = View.GONE
            logEmptyText.text = getString(
                R.string.outdoor_workout_log_empty,
                getWorkoutTypeLabel().lowercase(Locale.getDefault())
            )
            logEmptyText.visibility = View.VISIBLE
            return
        }

        logEmptyText.visibility = View.GONE
        logTableScroll.visibility = View.VISIBLE

        outdoorLogSessions
            .sortedByDescending { parseSessionDateTime(it.createdAt) ?: LocalDateTime.MIN }
            .forEach { session ->
                val itemView = layoutInflater.inflate(
                    R.layout.item_outdoor_log_table_row,
                    logTableRowsContainer,
                    false
                )

                itemView.findViewById<TextView>(R.id.tvOutdoorLogRouteValue).text =
                    buildRouteCellText(session)
                itemView.findViewById<TextView>(R.id.tvOutdoorLogDistanceValue).text =
                    formatDistance(session.distanceMeters)
                itemView.findViewById<TextView>(R.id.tvOutdoorLogSpeedValue).text =
                    formatSpeed(calculateAverageSpeedMetersPerSecond(session))
                itemView.findViewById<TextView>(R.id.tvOutdoorLogCaloriesValue).text =
                    formatCalories(session.caloriesBurned)
                itemView.findViewById<TextView>(R.id.tvOutdoorLogDateValue).text =
                    formatLogDate(session.createdAt)

                logTableRowsContainer.addView(itemView)
            }
    }

    private fun calculateAverageSpeedMetersPerSecond(session: WorkoutHistorySession): Double {
        return if (session.durationSeconds > 0 && session.distanceMeters > 0.0) {
            session.distanceMeters / session.durationSeconds.toDouble()
        } else {
            0.0
        }
    }

    private fun decodeOutdoorLogSessions(array: JSONArray): List<WorkoutHistorySession> {
        return WorkoutJsonParser.decodeSessions(array)
    }

    private fun parseSessionDateTime(rawDateTime: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(rawDateTime, HISTORY_FORMATTER)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLogDate(rawDateTime: String): String {
        val dateTime = parseSessionDateTime(rawDateTime) ?: return rawDateTime
        return dateTime.format(LOG_DATE_FORMATTER)
    }

    private fun buildRouteCellText(session: WorkoutHistorySession): String {
        return if (session.routeName.isNotBlank()) {
            session.routeName
        } else {
            getString(R.string.outdoor_workout_log_route_unnamed)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setDestination(point: LatLng) {
        showRouteNameDialog(point)
    }

    private fun showTargetDurationDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "30"
            setText(
                if (targetDurationSeconds > 0) {
                    (targetDurationSeconds / 60).toString()
                } else {
                    ""
                }
            )
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.outdoor_workout_target_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.outdoor_workout_target_dialog_save, null)
            .setNeutralButton(R.string.outdoor_workout_target_dialog_clear) { _, _ ->
                clearTargetDuration()
            }
            .setNegativeButton(R.string.outdoor_workout_target_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = input.text?.toString()?.trim()?.toIntOrNull()
                if (minutes == null || minutes <= 0) {
                    Toast.makeText(
                        this,
                        R.string.outdoor_workout_target_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                targetDurationSeconds = minutes * 60
                updateTargetDurationState(showToast = false)
                renderMetrics()
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_target_saved,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun clearTargetDuration() {
        targetDurationSeconds = 0
        hasReachedTargetDuration = false
        renderMetrics()
        Toast.makeText(this, R.string.outdoor_workout_target_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun showDistanceGoalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "5"
            setText(
                if (distanceGoalMeters > 0.0) {
                    String.format(Locale.US, "%.1f", distanceGoalMeters / 1000.0)
                } else {
                    ""
                }
            )
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.outdoor_workout_distance_goal_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.outdoor_workout_target_dialog_save, null)
            .setNegativeButton(R.string.outdoor_workout_target_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kilometers = input.text?.toString()?.trim()?.toDoubleOrNull()
                if (kilometers == null || kilometers <= 0.0) {
                    Toast.makeText(
                        this,
                        R.string.outdoor_workout_distance_goal_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                distanceGoalMeters = kilometers * 1000.0
                updateGoalProgressState(showToast = false)
                renderMetrics()
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_distance_goal_saved,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun clearDistanceGoal() {
        distanceGoalMeters = 0.0
        hasReachedDistanceGoal = false
        renderMetrics()
        Toast.makeText(this, R.string.outdoor_workout_distance_goal_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun showCaloriesGoalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "500"
            setText(
                if (caloriesGoal > 0.0) {
                    caloriesGoal.toInt().toString()
                } else {
                    ""
                }
            )
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.outdoor_workout_calories_goal_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.outdoor_workout_target_dialog_save, null)
            .setNegativeButton(R.string.outdoor_workout_target_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val targetCalories = input.text?.toString()?.trim()?.toDoubleOrNull()
                if (targetCalories == null || targetCalories <= 0.0) {
                    Toast.makeText(
                        this,
                        R.string.outdoor_workout_calories_goal_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                caloriesGoal = targetCalories
                updateGoalProgressState(showToast = false)
                renderMetrics()
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_calories_goal_saved,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun clearCaloriesGoal() {
        caloriesGoal = 0.0
        hasReachedCaloriesGoal = false
        renderMetrics()
        Toast.makeText(this, R.string.outdoor_workout_calories_goal_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun updateTargetDurationState(showToast: Boolean) {
        val reachedNow = targetDurationSeconds > 0 && secondsElapsed >= targetDurationSeconds
        if (reachedNow && !hasReachedTargetDuration) {
            hasReachedTargetDuration = true
            if (showToast) {
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_target_reached_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!reachedNow) {
            hasReachedTargetDuration = false
        }
    }

    private fun updateGoalProgressState(showToast: Boolean) {
        val reachedDistanceNow = distanceGoalMeters > 0.0 && totalDistanceMeters >= distanceGoalMeters
        if (reachedDistanceNow && !hasReachedDistanceGoal) {
            hasReachedDistanceGoal = true
            if (showToast) {
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_distance_goal_reached_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!reachedDistanceNow) {
            hasReachedDistanceGoal = false
        }

        val reachedCaloriesNow = caloriesGoal > 0.0 && caloriesBurned >= caloriesGoal
        if (reachedCaloriesNow && !hasReachedCaloriesGoal) {
            hasReachedCaloriesGoal = true
            if (showToast) {
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_calories_goal_reached_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!reachedCaloriesNow) {
            hasReachedCaloriesGoal = false
        }
    }

    private fun renderTargetDurationInfo() {
        if (targetDurationSeconds <= 0) {
            targetDurationValueText.text = getString(R.string.outdoor_workout_target_not_set)
            targetDurationProgress.visibility = View.GONE
            targetDurationProgress.progress = 0
            return
        }

        targetDurationValueText.text = getString(
            R.string.outdoor_workout_target_value,
            formatDuration(secondsElapsed),
            formatDuration(targetDurationSeconds)
        )
        targetDurationProgress.visibility = View.VISIBLE
        targetDurationProgress.progress = calculateTargetDurationProgress()
    }

    private fun renderGoalMonitoring() {
        renderDistanceGoalMonitoring()
        renderCaloriesGoalMonitoring()
    }

    private fun calculateTargetDurationProgress(): Int {
        if (targetDurationSeconds <= 0) {
            return 0
        }

        return ((secondsElapsed.toDouble() / targetDurationSeconds.toDouble()) * 100)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun redrawDestinationMarker() {
        if (!isMapReady) {
            return
        }

        destinationMarker?.remove()
        val point = destinationPoint ?: run {
            destinationMarker = null
            return
        }

        destinationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(point)
                .title(getDisplayRouteName())
        )
    }

    private fun renderDestinationInfo() {
        val point = destinationPoint
        if (point == null) {
            routeNameValueText.text = getString(R.string.outdoor_workout_route_name_empty)
            destinationValueText.text = getString(R.string.outdoor_workout_destination_empty)
            remainingValueText.text = getString(R.string.outdoor_workout_remaining_default)
            return
        }

        routeNameValueText.text = getDisplayRouteName()
        destinationValueText.text = if (routeName.isNotBlank()) {
            getString(R.string.outdoor_workout_destination_named_saved, routeName)
        } else {
            getString(R.string.outdoor_workout_destination_set)
        }

        val currentLocation = lastLocation
        if (currentLocation == null) {
            remainingValueText.text = getString(R.string.outdoor_workout_remaining_default)
            return
        }

        val remainingDistance = calculateDistanceToDestination(currentLocation, point)
        remainingValueText.text = if (hasArrivedAtDestination) {
            getString(R.string.outdoor_workout_destination_arrived)
        } else {
            getString(
                R.string.outdoor_workout_remaining_value,
                formatDistance(remainingDistance)
            )
        }
    }

    private fun updateDestinationState(currentLocation: Location) {
        val point = destinationPoint ?: return
        val remainingDistance = calculateDistanceToDestination(currentLocation, point)

        if (!hasArrivedAtDestination && remainingDistance <= DESTINATION_ARRIVAL_THRESHOLD_METERS) {
            hasArrivedAtDestination = true
            Toast.makeText(
                this,
                R.string.outdoor_workout_destination_reached_toast,
                Toast.LENGTH_SHORT
            ).show()
        }

        renderDestinationInfo()
    }

    private fun calculateDistanceToDestination(location: Location, destination: LatLng): Double {
        val destinationLocation = Location("destination").apply {
            latitude = destination.latitude
            longitude = destination.longitude
        }
        return location.distanceTo(destinationLocation).toDouble()
    }

    private fun getCurrentRemainingDistance(): Double {
        val point = destinationPoint ?: return 0.0
        val currentLocation = lastLocation ?: return 0.0
        return calculateDistanceToDestination(currentLocation, point)
    }

    private fun buildRouteJson(): String {
        val array = JSONArray()
        routePoints.forEachIndexed { index, point ->
            array.put(
                JSONObject().apply {
                    put("pointNumber", index + 1)
                    put("lat", point.latitude)
                    put("lng", point.longitude)
                }
            )
        }
        return array.toString()
    }

    private fun renderDistanceGoalMonitoring() {
        if (distanceGoalMeters <= 0.0) {
            distanceGoalPercentText.text = getString(R.string.outdoor_workout_goal_progress_default)
            distanceGoalValueText.text = getString(R.string.outdoor_workout_goal_not_set)
            distanceGoalStatusText.text = getString(R.string.outdoor_workout_goal_monitor_hint)
            distanceGoalProgress.progress = 0
            return
        }

        val progress = calculateGoalProgress(totalDistanceMeters, distanceGoalMeters)
        distanceGoalPercentText.text = getString(R.string.outdoor_workout_goal_percent_value, progress)
        distanceGoalValueText.text = getString(
            R.string.outdoor_workout_goal_value_distance,
            formatDistance(totalDistanceMeters),
            formatDistance(distanceGoalMeters)
        )
        distanceGoalStatusText.text = if (hasReachedDistanceGoal) {
            getString(R.string.outdoor_workout_goal_status_complete)
        } else {
            getString(R.string.outdoor_workout_goal_status_active, progress)
        }
        distanceGoalProgress.progress = progress
    }

    private fun renderCaloriesGoalMonitoring() {
        if (caloriesGoal <= 0.0) {
            caloriesGoalPercentText.text = getString(R.string.outdoor_workout_goal_progress_default)
            caloriesGoalValueText.text = getString(R.string.outdoor_workout_goal_not_set)
            caloriesGoalStatusText.text = getString(R.string.outdoor_workout_goal_monitor_hint)
            caloriesGoalProgress.progress = 0
            return
        }

        val progress = calculateGoalProgress(caloriesBurned, caloriesGoal)
        caloriesGoalPercentText.text = getString(R.string.outdoor_workout_goal_percent_value, progress)
        caloriesGoalValueText.text = getString(
            R.string.outdoor_workout_goal_value_calories,
            formatCalories(caloriesBurned),
            formatCalories(caloriesGoal)
        )
        caloriesGoalStatusText.text = if (hasReachedCaloriesGoal) {
            getString(R.string.outdoor_workout_goal_status_complete)
        } else {
            getString(R.string.outdoor_workout_goal_status_active, progress)
        }
        caloriesGoalProgress.progress = progress
    }

    private fun calculateGoalProgress(currentValue: Double, goalValue: Double): Int {
        if (goalValue <= 0.0) {
            return 0
        }

        return ((currentValue / goalValue) * 100).toInt().coerceIn(0, 100)
    }

    private fun getDisplayRouteName(): String {
        return routeName.ifBlank {
            getString(R.string.outdoor_workout_route_name_unnamed)
        }
    }

    private fun getCurrentMonthKey(): String {
        return LocalDate.now().format(MONTH_STORAGE_FORMATTER)
    }

    private fun getCurrentMonthDisplay(): String {
        return LocalDate.now().format(MONTH_DISPLAY_FORMATTER)
    }

    private fun loadMonthlyGoal() {
        val userId = sessionPrefs.getUserId()
        if (userId <= 0) {
            return
        }

        monthlyGoalSummaryText.text = getString(R.string.outdoor_workout_monthly_goal_loading)

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        monthlyGoalSummaryText.text = getString(
                            R.string.outdoor_workout_monthly_goal_empty,
                            getCurrentMonthDisplay()
                        )
                        return@Listener
                    }

                    val distanceGoalKm = payload.optString("distanceGoalKm").toDoubleOrNull() ?: 0.0
                    val caloriesGoalKcal = payload.optString("caloriesGoalKcal").toDoubleOrNull() ?: 0.0
                    applyMonthlyGoal(distanceGoalKm, caloriesGoalKcal)
                } catch (_: JSONException) {
                    monthlyGoalSummaryText.text = getString(
                        R.string.outdoor_workout_monthly_goal_empty,
                        getCurrentMonthDisplay()
                    )
                }
            },
            Response.ErrorListener {
                monthlyGoalSummaryText.text = getString(
                    R.string.outdoor_workout_monthly_goal_empty,
                    getCurrentMonthDisplay()
                )
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "getMonthlyGoal",
                    "userId" to userId.toString(),
                    "workoutType" to workoutType,
                    "goalMonth" to getCurrentMonthKey()
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun saveMonthlyGoal() {
        val userId = sessionPrefs.getUserId()
        if (userId <= 0) {
            Toast.makeText(this, R.string.outdoor_workout_save_user_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val distanceGoalKm = monthlyDistanceGoalInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val caloriesGoalKcal = monthlyCaloriesGoalInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

        if (distanceGoalKm <= 0.0 && caloriesGoalKcal <= 0.0) {
            Toast.makeText(this, R.string.outdoor_workout_monthly_goal_invalid, Toast.LENGTH_SHORT)
                .show()
            return
        }

        saveMonthlyGoalButton.isEnabled = false

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                saveMonthlyGoalButton.isEnabled = true
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        Toast.makeText(
                            this,
                            payload.optString(
                                "message",
                                getString(R.string.outdoor_workout_monthly_goal_save_failed)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                        return@Listener
                    }

                    applyMonthlyGoal(distanceGoalKm, caloriesGoalKcal)
                    Toast.makeText(
                        this,
                        getString(
                            R.string.outdoor_workout_monthly_goal_saved,
                            getWorkoutTypeLabel()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: JSONException) {
                    Toast.makeText(
                        this,
                        R.string.outdoor_workout_monthly_goal_save_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            Response.ErrorListener {
                saveMonthlyGoalButton.isEnabled = true
                Toast.makeText(
                    this,
                    R.string.outdoor_workout_monthly_goal_network_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "saveMonthlyGoal",
                    "userId" to userId.toString(),
                    "workoutType" to workoutType,
                    "goalMonth" to getCurrentMonthKey(),
                    "distanceGoalKm" to String.format(Locale.US, "%.2f", distanceGoalKm),
                    "caloriesGoalKcal" to String.format(Locale.US, "%.2f", caloriesGoalKcal)
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun applyMonthlyGoal(distanceGoalKm: Double, caloriesGoalKcal: Double) {
        monthlyDistanceGoalInput.setText(if (distanceGoalKm > 0.0) formatGoalDecimal(distanceGoalKm) else "")
        monthlyCaloriesGoalInput.setText(if (caloriesGoalKcal > 0.0) formatGoalDecimal(caloriesGoalKcal) else "")
        renderMonthlyGoalSummary(distanceGoalKm, caloriesGoalKcal)
    }

    private fun renderMonthlyGoalSummary(distanceGoalKm: Double, caloriesGoalKcal: Double) {
        if (distanceGoalKm <= 0.0 && caloriesGoalKcal <= 0.0) {
            monthlyGoalSummaryText.text = getString(
                R.string.outdoor_workout_monthly_goal_empty,
                getCurrentMonthDisplay()
            )
            return
        }

        val distanceText = if (distanceGoalKm > 0.0) {
            "${formatGoalDecimal(distanceGoalKm)} km"
        } else {
            getString(R.string.outdoor_workout_goal_not_set)
        }
        val caloriesText = if (caloriesGoalKcal > 0.0) {
            "${formatGoalDecimal(caloriesGoalKcal)} kcal"
        } else {
            getString(R.string.outdoor_workout_goal_not_set)
        }

        monthlyGoalSummaryText.text = getString(
            R.string.outdoor_workout_monthly_goal_summary,
            distanceText,
            caloriesText
        )
    }

    private fun formatGoalDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.getDefault(), "%.0f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun buildAutomaticRouteName(): String {
        return getString(
            R.string.outdoor_workout_route_name_auto,
            getWorkoutTypeLabel(),
            LocalDateTime.now().format(AUTO_ROUTE_FORMATTER)
        )
    }

    private fun showRouteNameDialog(point: LatLng) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = getString(R.string.outdoor_workout_route_dialog_hint)
            setText(routeName)
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.outdoor_workout_route_dialog_title)
            .setMessage(R.string.outdoor_workout_route_dialog_message)
            .setView(input)
            .setPositiveButton(R.string.outdoor_workout_target_dialog_save, null)
            .setNegativeButton(R.string.outdoor_workout_target_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                destinationPoint = point
                val enteredRouteName = input.text?.toString()?.trim().orEmpty()
                routeName = enteredRouteName.ifBlank { buildAutomaticRouteName() }
                hasArrivedAtDestination = false
                redrawDestinationMarker()
                renderDestinationInfo()

                val toastMessage = getString(R.string.outdoor_workout_destination_named_saved, routeName)
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    companion object {
        private const val EXTRA_WORKOUT_TYPE = "WORKOUT_TYPE"
        private const val WORKOUT_TYPE_WALKING = "walking"
        private const val WORKOUT_TYPE_RUNNING = "running"
        private const val WORKOUT_TYPE_CYCLING = "cycling"

        private const val KEY_WORKOUT_TYPE = "workout_type"
        private const val KEY_IS_TRACKING = "is_tracking"
        private const val KEY_IS_SAVING = "is_saving"
        private const val KEY_SECONDS_ELAPSED = "seconds_elapsed"
        private const val KEY_TOTAL_DISTANCE = "total_distance"
        private const val KEY_CURRENT_SPEED = "current_speed"
        private const val KEY_CALORIES_BURNED = "calories_burned"
        private const val KEY_ROUTE_POINTS = "route_points"
        private const val KEY_DESTINATION_POINT = "destination_point"
        private const val KEY_HAS_ARRIVED_AT_DESTINATION = "has_arrived_at_destination"
        private const val KEY_TARGET_DURATION_SECONDS = "target_duration_seconds"
        private const val KEY_HAS_REACHED_TARGET_DURATION = "has_reached_target_duration"
        private const val KEY_HAS_REACHED_DISTANCE_GOAL = "has_reached_distance_goal"
        private const val KEY_HAS_REACHED_CALORIES_GOAL = "has_reached_calories_goal"
        private const val KEY_DISTANCE_GOAL_METERS = "distance_goal_meters"
        private const val KEY_CALORIES_GOAL = "calories_goal"
        private const val KEY_ROUTE_NAME = "route_name"
        private const val KEY_SELECTED_PANEL = "selected_panel"

        private const val PANEL_TRACK = "track"
        private const val PANEL_LOGS = "logs"
        private const val PANEL_DATA = "data"

        private const val MIN_TRACKABLE_SEGMENT_METERS = 2.0
        private const val MAX_TRACKABLE_SEGMENT_METERS = 250.0
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 40f
        private const val DESTINATION_ARRIVAL_THRESHOLD_METERS = 30.0

        private val MONTH_STORAGE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM")
        private val MONTH_DISPLAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        private val HISTORY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val LOG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
        private val AUTO_ROUTE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    }
}
