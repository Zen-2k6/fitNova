package uk.ncc.fitNova

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
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
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.pow

class ProfileActivity : AppCompatActivity() {
    private lateinit var fullNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var ageSlider: Slider
    private lateinit var ageValueText: TextView
    private lateinit var weightSlider: Slider
    private lateinit var weightValueText: TextView
    private lateinit var heightSlider: Slider
    private lateinit var heightValueText: TextView
    private lateinit var bmiValueText: TextView
    private lateinit var bmiCategoryText: TextView
    private lateinit var profileInsightText: TextView
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var backButton: Button

    private var userId = -1
    private var gender = ""
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        applySystemBarInsets(findViewById(R.id.main))

        bindViews()
        bindActions()

        userId = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getInt("User_id", -1)
        if (userId <= 0) {
            Toast.makeText(this, R.string.profile_session_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        populateFromSession()
        fetchProfile()
    }

    private fun bindViews() {
        fullNameInput = findViewById(R.id.tieProfileFullName)
        emailInput = findViewById(R.id.tieProfileEmail)
        genderGroup = findViewById(R.id.rgProfileGender)
        ageSlider = findViewById(R.id.sldProfileAge)
        ageValueText = findViewById(R.id.tvProfileAgeValue)
        weightSlider = findViewById(R.id.sldProfileWeight)
        weightValueText = findViewById(R.id.tvProfileWeightValue)
        heightSlider = findViewById(R.id.sldProfileHeight)
        heightValueText = findViewById(R.id.tvProfileHeightValue)
        bmiValueText = findViewById(R.id.tvProfileBmiValue)
        bmiCategoryText = findViewById(R.id.tvProfileBmiCategory)
        profileInsightText = findViewById(R.id.tvProfileInsight)
        saveButton = findViewById(R.id.btnSaveProfile)
        logoutButton = findViewById(R.id.btnLogoutProfile)
        backButton = findViewById(R.id.btnBackProfile)
    }

    private fun bindActions() {
        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener {
            if (validateInputs()) {
                saveProfile()
            }
        }
        logoutButton.setOnClickListener {
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            openLoginScreen()
        }

        genderGroup.setOnCheckedChangeListener { _, checkedId ->
            gender = when (checkedId) {
                R.id.rbProfileMale -> getString(R.string.auth_gender_male)
                R.id.rbProfileFemale -> getString(R.string.auth_gender_female)
                else -> ""
            }
        }

        ageSlider.addOnChangeListener { _, _, _ -> renderMetricPreview() }
        weightSlider.addOnChangeListener { _, _, _ -> renderMetricPreview() }
        heightSlider.addOnChangeListener { _, _, _ -> renderMetricPreview() }
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

    private fun populateFromSession() {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val profile = ProfileData(
            fullName = sharedPref.getString("Full_name", "").orEmpty(),
            email = sharedPref.getString("Email", "").orEmpty(),
            gender = sharedPref.getString("Gender", "").orEmpty(),
            age = sharedPref.getInt("Age", 25).takeIf { it > 0 } ?: 25,
            weight = sharedPref.getInt("Weight", 70).takeIf { it > 0 } ?: 70,
            height = sharedPref.getInt("Height", 170).takeIf { it > 0 } ?: 170
        )
        applyProfile(profile, persistToSession = false)
    }

    private fun applyProfile(profile: ProfileData, persistToSession: Boolean) {
        fullNameInput.setText(profile.fullName)
        emailInput.setText(profile.email)
        setSliderValue(ageSlider, profile.age)
        setSliderValue(weightSlider, profile.weight)
        setSliderValue(heightSlider, profile.height)

        gender = profile.gender
        when (profile.gender) {
            getString(R.string.auth_gender_male) -> genderGroup.check(R.id.rbProfileMale)
            getString(R.string.auth_gender_female) -> genderGroup.check(R.id.rbProfileFemale)
            else -> genderGroup.clearCheck()
        }

        renderMetricPreview()

        if (persistToSession) {
            updateSession(profile)
        }
    }

    private fun setSliderValue(slider: Slider, value: Int) {
        slider.value = value.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
    }

    private fun renderMetricPreview() {
        val age = ageSlider.value.toInt()
        val weight = weightSlider.value.toInt()
        val height = heightSlider.value.toInt()

        ageValueText.text = getString(R.string.profile_age_value, age)
        weightValueText.text = getString(R.string.profile_weight_value, weight)
        heightValueText.text = getString(R.string.profile_height_value, height)

        if (weight <= 0 || height <= 0) {
            bmiValueText.text = getString(R.string.fitness_metric_unknown)
            bmiCategoryText.text = getString(R.string.fitness_bmi_unknown)
            profileInsightText.text = getString(R.string.fitness_tip_missing)
            return
        }

        val bmi = calculateBmi(weight, height)
        val bmiCategory = getBmiCategory(bmi)
        bmiValueText.text = getString(R.string.fitness_bmi_value, bmi)
        bmiCategoryText.text = getString(bmiCategory.first)
        profileInsightText.text = getString(bmiCategory.second)
    }

    private fun fetchProfile() {
        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.PROFILE_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") != "true") {
                        return@Listener
                    }

                    val profile = ProfileData(
                        fullName = payload.optString("FullName"),
                        email = payload.optString("Email"),
                        gender = payload.optString("Gender"),
                        age = payload.optInt("Age"),
                        weight = payload.optInt("Weight"),
                        height = payload.optInt("Height")
                    )
                    applyProfile(profile, persistToSession = true)
                } catch (_: JSONException) {
                }
            },
            Response.ErrorListener { }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "phpFunction" to "getProfile",
                    "userId" to userId.toString()
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun validateInputs(): Boolean {
        val fullName = fullNameInput.text?.toString()?.trim().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty()

        if (fullName.isBlank()) {
            fullNameInput.error = getString(R.string.profile_error_name)
            return false
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = getString(R.string.profile_error_email)
            return false
        }
        if (gender.isBlank()) {
            Toast.makeText(this, R.string.profile_error_gender, Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveProfile() {
        if (isSaving) {
            return
        }

        isSaving = true
        saveButton.isEnabled = false
        saveButton.text = getString(R.string.profile_saving_button)

        val request = object : StringRequest(
            Request.Method.POST,
            BackendConfig.PROFILE_URL,
            Response.Listener<String> { response ->
                try {
                    val payload = JSONObject(response.trim())
                    if (payload.optString("response") == "true") {
                        val profile = currentProfile()
                        updateSession(profile)
                        Toast.makeText(this, R.string.profile_save_success, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        resetSaveButton()
                        val message = payload.optString(
                            "message",
                            getString(R.string.profile_save_failed)
                        )
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                } catch (_: JSONException) {
                    resetSaveButton()
                    Toast.makeText(this, R.string.profile_save_failed, Toast.LENGTH_LONG).show()
                }
            },
            Response.ErrorListener {
                resetSaveButton()
                Toast.makeText(this, R.string.profile_save_network_error, Toast.LENGTH_LONG).show()
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                val profile = currentProfile()
                return hashMapOf(
                    "phpFunction" to "updateProfile",
                    "userId" to userId.toString(),
                    "fullName" to profile.fullName,
                    "email" to profile.email,
                    "gender" to profile.gender,
                    "age" to profile.age.toString(),
                    "weight" to profile.weight.toString(),
                    "height" to profile.height.toString()
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun currentProfile(): ProfileData {
        return ProfileData(
            fullName = fullNameInput.text?.toString()?.trim().orEmpty(),
            email = emailInput.text?.toString()?.trim().orEmpty(),
            gender = gender,
            age = ageSlider.value.toInt(),
            weight = weightSlider.value.toInt(),
            height = heightSlider.value.toInt()
        )
    }

    private fun updateSession(profile: ProfileData) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("Full_name", profile.fullName)
            putString("Email", profile.email)
            putString("Gender", profile.gender)
            putInt("Age", profile.age)
            putInt("Weight", profile.weight)
            putInt("Height", profile.height)
            apply()
        }
    }

    private fun resetSaveButton() {
        isSaving = false
        saveButton.isEnabled = true
        saveButton.text = getString(R.string.profile_save_button)
    }

    private fun openLoginScreen() {
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun calculateBmi(weightKg: Int, heightCm: Int): Double {
        val heightMeters = heightCm / 100.0
        return weightKg / heightMeters.pow(2)
    }

    private fun getBmiCategory(bmi: Double): Pair<Int, Int> {
        return when {
            bmi < 18.5 -> Pair(R.string.fitness_bmi_underweight, R.string.fitness_tip_underweight)
            bmi < 25.0 -> Pair(R.string.fitness_bmi_healthy, R.string.fitness_tip_healthy)
            bmi < 30.0 -> Pair(R.string.fitness_bmi_overweight, R.string.fitness_tip_overweight)
            else -> Pair(R.string.fitness_bmi_obesity, R.string.fitness_tip_obesity)
        }
    }
}

data class ProfileData(
    val fullName: String,
    val email: String,
    val gender: String,
    val age: Int,
    val weight: Int,
    val height: Int
)
