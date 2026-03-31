package uk.ncc.fitNova

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class WeightLiftingActivity : AppCompatActivity() {
    private lateinit var sessionTimerText: TextView
    private lateinit var restTimerText: TextView
    private lateinit var totalSetsText: TextView
    private lateinit var totalRepsText: TextView
    private lateinit var totalVolumeText: TextView
    private lateinit var exerciseNameInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var repsInput: TextInputEditText
    private lateinit var sessionToggleButton: MaterialButton
    private lateinit var finishSessionButton: MaterialButton
    private lateinit var addSetButton: MaterialButton
    private lateinit var rest60Button: MaterialButton
    private lateinit var rest90Button: MaterialButton
    private lateinit var resetRestButton: MaterialButton
    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyHistoryText: TextView

    private var isSessionRunning = false
    private var sessionSeconds = 0
    private var isRestRunning = false
    private var restSecondsRemaining = 0
    private var totalSets = 0
    private var totalReps = 0
    private var totalVolume = 0.0
    private var isSaving = false
    private val setEntries = mutableListOf<WeightLiftingSetEntry>()

    private val sessionHandler = Handler(Looper.getMainLooper())
    private val restHandler = Handler(Looper.getMainLooper())

    private val sessionRunnable = object : Runnable {
        override fun run() {
            if (!isSessionRunning) {
                return
            }

            sessionSeconds++
            renderSessionTimer()
            sessionHandler.postDelayed(this, 1000)
        }
    }

    private val restRunnable = object : Runnable {
        override fun run() {
            if (!isRestRunning) {
                return
            }

            if (restSecondsRemaining > 0) {
                restSecondsRemaining--
                renderRestTimer()
            }

            if (restSecondsRemaining == 0) {
                stopRestTimer(showFinishedToast = true)
            } else {
                restHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_weight_lifting)
        applySystemBarInsets(findViewById(R.id.main))

        bindViews()
        bindActions()

        if (savedInstanceState == null) {
            renderSessionTimer()
            renderRestTimer()
            renderSummary()
            renderHistory()
            updateSessionButton()
        } else {
            restoreState(savedInstanceState)
        }
    }

    private fun bindViews() {
        sessionTimerText = findViewById(R.id.tvSessionTimer)
        restTimerText = findViewById(R.id.tvRestTimer)
        totalSetsText = findViewById(R.id.tvTotalSetsValue)
        totalRepsText = findViewById(R.id.tvTotalRepsValue)
        totalVolumeText = findViewById(R.id.tvTotalVolumeValue)
        exerciseNameInput = findViewById(R.id.tieExerciseName)
        weightInput = findViewById(R.id.tieWeightValue)
        repsInput = findViewById(R.id.tieRepsValue)
        sessionToggleButton = findViewById(R.id.btnToggleSession)
        finishSessionButton = findViewById(R.id.btnFinishSession)
        addSetButton = findViewById(R.id.btnAddSet)
        rest60Button = findViewById(R.id.btnRest60)
        rest90Button = findViewById(R.id.btnRest90)
        resetRestButton = findViewById(R.id.btnResetRest)
        historyContainer = findViewById(R.id.llSetHistory)
        emptyHistoryText = findViewById(R.id.tvEmptyHistory)
    }

    private fun bindActions() {
        sessionToggleButton.setOnClickListener {
            if (isSessionRunning) {
                pauseSession()
            } else {
                startSession()
            }
        }

        finishSessionButton.setOnClickListener {
            saveSessionAndFinish()
        }

        addSetButton.setOnClickListener {
            addSet()
        }

        rest60Button.setOnClickListener {
            startRestTimer(60)
        }

        rest90Button.setOnClickListener {
            startRestTimer(90)
        }

        resetRestButton.setOnClickListener {
            stopRestTimer(showFinishedToast = false, resetToReady = true)
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

    private fun startSession() {
        if (isSessionRunning) {
            return
        }

        isSessionRunning = true
        updateSessionButton()
        sessionHandler.removeCallbacks(sessionRunnable)
        sessionHandler.postDelayed(sessionRunnable, 1000)
    }

    private fun pauseSession() {
        isSessionRunning = false
        sessionHandler.removeCallbacks(sessionRunnable)
        updateSessionButton()
    }

    private fun updateSessionButton() {
        val labelRes = when {
            isSessionRunning -> R.string.weight_lifting_pause_session
            sessionSeconds > 0 -> R.string.weight_lifting_resume_session
            else -> R.string.weight_lifting_start_session
        }
        val colorRes = if (isSessionRunning) R.color.red else R.color.auth_button_blue

        sessionToggleButton.text = getString(labelRes)
        sessionToggleButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun addSet() {
        val exercise = exerciseNameInput.text?.toString()?.trim().orEmpty()
        val weightValue = weightInput.text?.toString()?.trim().orEmpty().toDoubleOrNull()
        val repsValue = repsInput.text?.toString()?.trim().orEmpty().toIntOrNull()

        if (exercise.isEmpty()) {
            exerciseNameInput.error = getString(R.string.weight_lifting_error_exercise)
            return
        }

        if (weightValue == null || weightValue <= 0.0) {
            weightInput.error = getString(R.string.weight_lifting_error_weight)
            return
        }

        if (repsValue == null || repsValue <= 0) {
            repsInput.error = getString(R.string.weight_lifting_error_reps)
            return
        }

        totalSets++
        totalReps += repsValue
        totalVolume += weightValue * repsValue

        setEntries.add(
            WeightLiftingSetEntry(
                exercise = exercise,
                weightKg = weightValue,
                reps = repsValue
            )
        )

        renderSummary()
        renderHistory()

        if (!isSessionRunning) {
            startSession()
        }

        repsInput.text?.clear()
        repsInput.requestFocus()
        Toast.makeText(this, R.string.weight_lifting_set_added, Toast.LENGTH_SHORT).show()
    }

    private fun renderSummary() {
        totalSetsText.text = totalSets.toString()
        totalRepsText.text = totalReps.toString()
        totalVolumeText.text = getString(
            R.string.weight_lifting_volume_value,
            formatWeight(totalVolume)
        )
    }

    private fun renderSessionTimer() {
        sessionTimerText.text = formatDuration(sessionSeconds)
    }

    private fun renderRestTimer() {
        restTimerText.text = if (restSecondsRemaining > 0) {
            formatDuration(restSecondsRemaining)
        } else {
            getString(R.string.weight_lifting_rest_ready)
        }
    }

    private fun startRestTimer(seconds: Int) {
        restSecondsRemaining = seconds
        isRestRunning = true
        renderRestTimer()
        restHandler.removeCallbacks(restRunnable)
        restHandler.postDelayed(restRunnable, 1000)
    }

    private fun stopRestTimer(showFinishedToast: Boolean, resetToReady: Boolean = false) {
        isRestRunning = false
        restHandler.removeCallbacks(restRunnable)

        if (resetToReady || restSecondsRemaining <= 0) {
            restSecondsRemaining = 0
        }

        renderRestTimer()

        if (showFinishedToast) {
            Toast.makeText(this, R.string.weight_lifting_rest_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()

        emptyHistoryText.visibility = if (setEntries.isEmpty()) View.VISIBLE else View.GONE

        setEntries.forEachIndexed { index, item ->
            historyContainer.addView(createHistoryRow(formatHistoryEntry(index + 1, item)))
        }
    }

    private fun formatHistoryEntry(index: Int, entry: WeightLiftingSetEntry): String {
        return getString(
            R.string.weight_lifting_history_item,
            index,
            entry.exercise,
            formatWeight(entry.weightKg),
            entry.reps
        )
    }

    private fun createHistoryRow(label: String): TextView {
        val textView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topMargin = dpToPx(12)
        textView.layoutParams = layoutParams
        textView.text = label
        textView.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))
        textView.textSize = 15f
        textView.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(ContextCompat.getColor(this@WeightLiftingActivity, R.color.fitness_primary_soft))
        }
        return textView
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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

    private fun saveSessionAndFinish() {
        if (isSaving) {
            return
        }

        if (setEntries.isEmpty()) {
            Toast.makeText(this, R.string.weight_lifting_session_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("User_id", -1)
        if (userId <= 0) {
            Toast.makeText(this, R.string.weight_lifting_save_user_missing, Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        finishSessionButton.isEnabled = false
        finishSessionButton.text = getString(R.string.weight_lifting_saving)

        pauseSession()

        val saveRequest = object : StringRequest(
            Request.Method.POST,
            BackendConfig.WORKOUT_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    val success = payload.optString("response") == "true"
                    if (success) {
                        Toast.makeText(
                            this,
                            R.string.weight_lifting_save_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        resetSaveState()
                        val errorMessage = payload.optString(
                            "message",
                            getString(R.string.weight_lifting_save_failed)
                        )
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                } catch (exception: JSONException) {
                    Log.e("WeightLiftingSave", "Invalid JSON response: ${exception.message}")
                    resetSaveState()
                    Toast.makeText(
                        this,
                        R.string.weight_lifting_save_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("WeightLiftingSave", "Network error: $error")
                resetSaveState()
                Toast.makeText(
                    this,
                    R.string.weight_lifting_save_network_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "saveWorkoutSession",
                    "userId" to userId.toString(),
                    "workoutType" to "weight_lifting",
                    "durationSeconds" to sessionSeconds.toString(),
                    "totalSets" to totalSets.toString(),
                    "totalReps" to totalReps.toString(),
                    "totalVolume" to String.format(Locale.US, "%.2f", totalVolume),
                    "setLogJson" to buildSetLogJson()
                )
            }
        }

        Volley.newRequestQueue(this).add(saveRequest)
    }

    private fun buildSetLogJson(): String {
        val jsonArray = JSONArray()
        setEntries.forEachIndexed { index, entry ->
            jsonArray.put(
                JSONObject().apply {
                    put("setNumber", index + 1)
                    put("exercise", entry.exercise)
                    put("weightKg", entry.weightKg)
                    put("reps", entry.reps)
                }
            )
        }
        return jsonArray.toString()
    }

    private fun resetSaveState() {
        isSaving = false
        finishSessionButton.isEnabled = true
        finishSessionButton.text = getString(R.string.weight_lifting_finish_session)
    }

    private fun restoreState(bundle: Bundle) {
        sessionSeconds = bundle.getInt(KEY_SESSION_SECONDS)
        isSessionRunning = bundle.getBoolean(KEY_IS_SESSION_RUNNING)
        restSecondsRemaining = bundle.getInt(KEY_REST_SECONDS_REMAINING)
        isRestRunning = bundle.getBoolean(KEY_IS_REST_RUNNING)
        totalSets = bundle.getInt(KEY_TOTAL_SETS)
        totalReps = bundle.getInt(KEY_TOTAL_REPS)
        totalVolume = bundle.getDouble(KEY_TOTAL_VOLUME)
        isSaving = bundle.getBoolean(KEY_IS_SAVING)

        exerciseNameInput.setText(bundle.getString(KEY_EXERCISE_NAME).orEmpty())
        weightInput.setText(bundle.getString(KEY_WEIGHT_VALUE).orEmpty())
        repsInput.setText(bundle.getString(KEY_REPS_VALUE).orEmpty())

        setEntries.clear()
        setEntries.addAll(decodeSetEntries(bundle.getString(KEY_SET_HISTORY_JSON).orEmpty()))

        renderSessionTimer()
        renderRestTimer()
        renderSummary()
        renderHistory()
        updateSessionButton()
        if (isSaving) {
            finishSessionButton.isEnabled = false
            finishSessionButton.text = getString(R.string.weight_lifting_saving)
        } else {
            resetSaveState()
        }

        if (isSessionRunning) {
            sessionHandler.removeCallbacks(sessionRunnable)
            sessionHandler.postDelayed(sessionRunnable, 1000)
        }

        if (isRestRunning && restSecondsRemaining > 0) {
            restHandler.removeCallbacks(restRunnable)
            restHandler.postDelayed(restRunnable, 1000)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SESSION_SECONDS, sessionSeconds)
        outState.putBoolean(KEY_IS_SESSION_RUNNING, isSessionRunning)
        outState.putInt(KEY_REST_SECONDS_REMAINING, restSecondsRemaining)
        outState.putBoolean(KEY_IS_REST_RUNNING, isRestRunning)
        outState.putInt(KEY_TOTAL_SETS, totalSets)
        outState.putInt(KEY_TOTAL_REPS, totalReps)
        outState.putDouble(KEY_TOTAL_VOLUME, totalVolume)
        outState.putBoolean(KEY_IS_SAVING, isSaving)
        outState.putString(KEY_EXERCISE_NAME, exerciseNameInput.text?.toString().orEmpty())
        outState.putString(KEY_WEIGHT_VALUE, weightInput.text?.toString().orEmpty())
        outState.putString(KEY_REPS_VALUE, repsInput.text?.toString().orEmpty())
        outState.putString(KEY_SET_HISTORY_JSON, buildSetLogJson())
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
        } catch (exception: JSONException) {
            Log.e("WeightLiftingState", "Failed to decode state: ${exception.message}")
            emptyList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionHandler.removeCallbacks(sessionRunnable)
        restHandler.removeCallbacks(restRunnable)
    }

    companion object {
        private const val KEY_SESSION_SECONDS = "session_seconds"
        private const val KEY_IS_SESSION_RUNNING = "is_session_running"
        private const val KEY_REST_SECONDS_REMAINING = "rest_seconds_remaining"
        private const val KEY_IS_REST_RUNNING = "is_rest_running"
        private const val KEY_TOTAL_SETS = "total_sets"
        private const val KEY_TOTAL_REPS = "total_reps"
        private const val KEY_TOTAL_VOLUME = "total_volume"
        private const val KEY_IS_SAVING = "is_saving"
        private const val KEY_SET_HISTORY_JSON = "set_history_json"
        private const val KEY_EXERCISE_NAME = "exercise_name"
        private const val KEY_WEIGHT_VALUE = "weight_value"
        private const val KEY_REPS_VALUE = "reps_value"
    }
}

data class WeightLiftingSetEntry(
    val exercise: String,
    val weightKg: Double,
    val reps: Int
)
