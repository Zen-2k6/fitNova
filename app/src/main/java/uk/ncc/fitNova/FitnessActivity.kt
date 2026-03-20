package uk.ncc.fitNova

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.pow

class FitnessActivity : AppCompatActivity() {
    private lateinit var greetingText: TextView
    private lateinit var weightValueText: TextView
    private lateinit var heightValueText: TextView
    private lateinit var bmiValueText: TextView
    private lateinit var bmiCategoryText: TextView
    private lateinit var weightRangeText: TextView
    private lateinit var waterGoalText: TextView
    private lateinit var tipText: TextView
    private lateinit var logoutButton: Button

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

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("IS_LOGGED_IN", false)) {
            Toast.makeText(this, R.string.fitness_login_required, Toast.LENGTH_SHORT).show()
            openLoginScreen(clearTask = true)
            return
        }

        val fullName = sharedPref.getString("Full_name", null).orEmpty().ifBlank {
            getString(R.string.fitness_unknown_name)
        }
        val weight = sharedPref.getInt("Weight", 0)
        val height = sharedPref.getInt("Height", 0)

        renderDashboard(fullName, weight, height)

        logoutButton.setOnClickListener {
            sharedPref.edit().clear().apply()
            openLoginScreen(clearTask = true)
        }
    }

    private fun bindViews() {
        greetingText = findViewById(R.id.tvGreeting)
        weightValueText = findViewById(R.id.tvWeightValue)
        heightValueText = findViewById(R.id.tvHeightValue)
        bmiValueText = findViewById(R.id.tvBmiValue)
        bmiCategoryText = findViewById(R.id.tvBmiCategory)
        weightRangeText = findViewById(R.id.tvWeightRangeValue)
        waterGoalText = findViewById(R.id.tvWaterGoalValue)
        tipText = findViewById(R.id.tvCoachingTip)
        logoutButton = findViewById(R.id.btnLogout)
    }

    private fun renderDashboard(fullName: String, weight: Int, height: Int) {
        greetingText.text = getString(R.string.fitness_greeting_name, fullName)

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
}
