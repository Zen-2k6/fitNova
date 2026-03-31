package uk.ncc.fitNova

import android.Manifest
import android.content.Context
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class WalkingMapActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var sessionTitleText: TextView
    private lateinit var durationValueText: TextView
    private lateinit var distanceValueText: TextView
    private lateinit var caloriesValueText: TextView
    private lateinit var destinationValueText: TextView
    private lateinit var remainingValueText: TextView
    private lateinit var targetDurationValueText: TextView
    private lateinit var toggleButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var setTargetDurationButton: MaterialButton
    private lateinit var targetDurationProgress: LinearProgressIndicator

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
    private var caloriesBurned = 0.0
    private var hasCenteredMap = false
    private var hasArrivedAtDestination = false
    private var hasReachedTargetDuration = false
    private var lastLocation: Location? = null
    private var destinationPoint: LatLng? = null
    private var targetDurationSeconds = 0
    private val routePoints = arrayListOf<LatLng>()

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
        setContentView(R.layout.activity_walking_map)
        applySystemBarInsets(findViewById(R.id.main))

        workoutType = savedInstanceState?.getString(KEY_WORKOUT_TYPE)
            ?: intent.getStringExtra(EXTRA_WORKOUT_TYPE)?.lowercase(Locale.US)
            ?: WORKOUT_TYPE_WALKING

        bindViews()
        setupLocationTracking()
        bindActions()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            renderMetrics()
            updateToggleButton()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun bindViews() {
        sessionTitleText = findViewById(R.id.tvOutdoorSessionTitle)
        durationValueText = findViewById(R.id.tvOutdoorDurationValue)
        distanceValueText = findViewById(R.id.tvOutdoorDistanceValue)
        caloriesValueText = findViewById(R.id.tvOutdoorCaloriesValue)
        destinationValueText = findViewById(R.id.tvOutdoorDestinationValue)
        remainingValueText = findViewById(R.id.tvOutdoorRemainingValue)
        targetDurationValueText = findViewById(R.id.tvOutdoorTargetDurationValue)
        toggleButton = findViewById(R.id.btnToggleOutdoorWorkout)
        finishButton = findViewById(R.id.btnFinishOutdoorWorkout)
        setTargetDurationButton = findViewById(R.id.btnSetOutdoorDuration)
        targetDurationProgress = findViewById(R.id.progressOutdoorTargetDuration)

        sessionTitleText.text = getString(
            R.string.outdoor_workout_session_title,
            getWorkoutTypeLabel()
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
        isTracking = true
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000)
        startLocationUpdates()
        updateToggleButton()
    }

    private fun pauseTracking() {
        isTracking = false
        timerHandler.removeCallbacks(timerRunnable)
        stopLocationUpdates()
        updateToggleButton()
    }

    private fun updateToggleButton() {
        val labelRes = when {
            isTracking -> R.string.outdoor_workout_pause
            secondsElapsed > 0 || totalDistanceMeters > 0.0 -> R.string.outdoor_workout_resume
            else -> R.string.outdoor_workout_start
        }
        val colorRes = if (isTracking) R.color.red else R.color.auth_button_blue

        toggleButton.text = getString(labelRes)
        toggleButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
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
        if (
            segmentDistance < MIN_TRACKABLE_SEGMENT_METERS ||
            segmentDistance > MAX_TRACKABLE_SEGMENT_METERS
        ) {
            return
        }

        totalDistanceMeters += segmentDistance
        routePoints.add(point)
        recalculateCalories()
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
        caloriesValueText.text = formatCalories(caloriesBurned)
        renderTargetDurationInfo()
        renderDestinationInfo()
    }

    private fun recalculateCalories() {
        val weightKg = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getInt("Weight", 0)
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

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("User_id", -1)
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
        finishButton.text = getString(R.string.outdoor_workout_finish)
        updateToggleButton()
    }

    private fun restoreState(bundle: Bundle) {
        isTracking = bundle.getBoolean(KEY_IS_TRACKING)
        isSaving = bundle.getBoolean(KEY_IS_SAVING)
        secondsElapsed = bundle.getInt(KEY_SECONDS_ELAPSED)
        totalDistanceMeters = bundle.getDouble(KEY_TOTAL_DISTANCE)
        caloriesBurned = bundle.getDouble(KEY_CALORIES_BURNED)
        hasArrivedAtDestination = bundle.getBoolean(KEY_HAS_ARRIVED_AT_DESTINATION)
        hasReachedTargetDuration = bundle.getBoolean(KEY_HAS_REACHED_TARGET_DURATION)
        targetDurationSeconds = bundle.getInt(KEY_TARGET_DURATION_SECONDS)
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

        if (isSaving) {
            toggleButton.isEnabled = false
            finishButton.isEnabled = false
            setTargetDurationButton.isEnabled = false
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
        outState.putDouble(KEY_CALORIES_BURNED, caloriesBurned)
        outState.putBoolean(KEY_HAS_ARRIVED_AT_DESTINATION, hasArrivedAtDestination)
        outState.putBoolean(KEY_HAS_REACHED_TARGET_DURATION, hasReachedTargetDuration)
        outState.putInt(KEY_TARGET_DURATION_SECONDS, targetDurationSeconds)
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

    private fun formatCalories(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f kcal", value)
    }

    private fun getWorkoutTypeLabel(): String {
        return when (workoutType) {
            WORKOUT_TYPE_RUNNING -> getString(R.string.fitness_activity_running)
            WORKOUT_TYPE_CYCLING -> getString(R.string.fitness_activity_cycling)
            else -> getString(R.string.fitness_activity_walking)
        }
    }

    private fun setDestination(point: LatLng) {
        destinationPoint = point
        hasArrivedAtDestination = false
        redrawDestinationMarker()
        renderDestinationInfo()
        Toast.makeText(this, R.string.outdoor_workout_destination_saved, Toast.LENGTH_SHORT).show()
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
                .title(getString(R.string.outdoor_workout_destination_label))
        )
    }

    private fun renderDestinationInfo() {
        val point = destinationPoint
        if (point == null) {
            destinationValueText.text = getString(R.string.outdoor_workout_destination_empty)
            remainingValueText.text = getString(R.string.outdoor_workout_remaining_default)
            return
        }

        destinationValueText.text = getString(
            R.string.outdoor_workout_destination_set
        )

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
        private const val KEY_CALORIES_BURNED = "calories_burned"
        private const val KEY_ROUTE_POINTS = "route_points"
        private const val KEY_DESTINATION_POINT = "destination_point"
        private const val KEY_HAS_ARRIVED_AT_DESTINATION = "has_arrived_at_destination"
        private const val KEY_TARGET_DURATION_SECONDS = "target_duration_seconds"
        private const val KEY_HAS_REACHED_TARGET_DURATION = "has_reached_target_duration"

        private const val MIN_TRACKABLE_SEGMENT_METERS = 2.0
        private const val MAX_TRACKABLE_SEGMENT_METERS = 250.0
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 40f
        private const val DESTINATION_ARRIVAL_THRESHOLD_METERS = 30.0
    }
}
