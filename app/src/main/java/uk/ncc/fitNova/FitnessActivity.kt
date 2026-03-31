package uk.ncc.fitNova

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var historyButton: View
    private lateinit var analysisButton: View
    private lateinit var cardRunning: View
    private lateinit var cardWalking: View
    private lateinit var cardWeightLifting: View
    private lateinit var cardCycling: View

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
    }

    override fun onResume() {
        super.onResume()
        renderDashboardFromSession()
    }

    private fun bindActions() {
        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        historyButton.setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }
        analysisButton.setOnClickListener {
            startActivity(Intent(this, AnalysisReportActivity::class.java))
        }

        cardRunning.setOnClickListener { startOutdoorWorkout("running") }
        cardWalking.setOnClickListener { startOutdoorWorkout("walking") }
        cardWeightLifting.setOnClickListener {
            val intent = Intent(this, WeightLiftingActivity::class.java)
            startActivity(intent)
        }
        cardCycling.setOnClickListener { startOutdoorWorkout("cycling") }
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

        renderDashboard(fullName, email, gender, age, weight, height)
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
        historyButton = findViewById(R.id.btnWorkoutHistory)
        analysisButton = findViewById(R.id.btnAnalysisReport)
        cardRunning = findViewById(R.id.cardRunning)
        cardWalking = findViewById(R.id.cardWalking)
        cardWeightLifting = findViewById(R.id.cardWeightLifting)
        cardCycling = findViewById(R.id.cardCycling)
    }

    private fun renderDashboard(
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

    private fun openLoginScreen(clearTask: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        if (clearTask) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startOutdoorWorkout(workoutType: String) {
        val intent = Intent(this, WalkingMapActivity::class.java)
        intent.putExtra("WORKOUT_TYPE", workoutType)
        startActivity(intent)
    }
}
