package uk.ncc.fitNova.auth

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
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import uk.ncc.fitNova.R
import uk.ncc.fitNova.data.remote.BackendConfig
import uk.ncc.fitNova.ui.applyBlackSystemBars


class RegistrationActivity : AppCompatActivity() {
    private lateinit var fnameTet: TextInputEditText
    private lateinit var emailTet: TextInputEditText
    private lateinit var passwordTet: TextInputEditText
    private lateinit var confirmPassTet: TextInputEditText
    private lateinit var rgGenderTet: RadioGroup
    private lateinit var ageSld: Slider
    private lateinit var ageVal: TextView
    private lateinit var weightSld: Slider
    private lateinit var weightVal: TextView
    private lateinit var heightSld: Slider
    private lateinit var heightVal: TextView
    private var gender: String = ""
    private var age: String = "0"
    private var weight: String = "0"
    private var height: String = "0"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registration)
        applyBlackSystemBars(this)
        applySystemBarInsets(findViewById(R.id.svRegister))

        //UI Initialization
        fnameTet = findViewById<TextInputEditText>(R.id.tieFullName)
        emailTet = findViewById<TextInputEditText>(R.id.tieEmail)
        passwordTet = findViewById<TextInputEditText>(R.id.tiePassword1)
        confirmPassTet = findViewById<TextInputEditText>(R.id.tiePassword2)
        rgGenderTet = findViewById<RadioGroup>(R.id.rgGender)
        ageSld = findViewById<Slider>(R.id.sldAge)
        ageVal = findViewById<TextView>(R.id.sldValAge)
        weightSld = findViewById<Slider>(R.id.sldWeight)
        weightVal = findViewById<TextView>(R.id.sldValWeight)
        heightSld = findViewById<Slider>(R.id.sldHeight)
        heightVal = findViewById<TextView>(R.id.sldValHeight)
        var btnReg = findViewById<Button>(R.id.btnRegister)

        syncSliderValues()

        //slider listener
        ageSld.addOnChangeListener { _, value, _ ->
            ageVal.text = "Age: ${value.toInt()}"
            age = value.toInt().toString()

        }

        weightSld.addOnChangeListener { _, value, _ ->
            weightVal.text = "Weight(kg): ${value.toInt()}"
            weight = value.toInt().toString()

        }
        heightSld.addOnChangeListener { _, value, _ ->
            heightVal.text = "Height(cm): ${value.toInt()}"
            height = value.toInt().toString()
        }

        //Radio Group
        rgGenderTet.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rbMale -> gender = "Male"
                R.id.rbFemale -> gender = "Female"

            }
        }


        //Onclick fun of Register
        btnReg.setOnClickListener {
            if (inputValidates()) {
                register()
            } else {
                Snackbar.make(btnReg, "Please fill all fields correctly", Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }//end of onCreate()

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

    private fun syncSliderValues() {
        age = ageSld.value.toInt().toString()
        weight = weightSld.value.toInt().toString()
        height = heightSld.value.toInt().toString()

        ageVal.text = "Age: $age"
        weightVal.text = "Weight(kg): $weight"
        heightVal.text = "Height(cm): $height"
    }

    private fun getUserData(): UserData {
        return UserData(
            fullName = fnameTet.text.toString().trim(),
            email = emailTet.text.toString().trim(),
            password = passwordTet.text.toString().trim(),
            confirmPass = confirmPassTet.text.toString().trim(),
            age = age,
            weight = weight,
            height = height,
            gender = gender
        )
    }//get UserData End

    //inputValidates() function

    private fun inputValidates(): Boolean {
        val userData = getUserData()

        //FullName Validates
        if (userData.fullName.isEmpty()) {
            fnameTet.error = "Full Name can't be empty"
            return false
        }
        if(userData.email.isEmpty()){
            emailTet.error="Email can't be empty"
            return false
        }
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(userData.email).matches()){
            emailTet.error="Invalid email format"
            return false
        }
        if(userData.password.isEmpty()){
            passwordTet.error="Password can't be empty"
            return false
        }
        if(userData.confirmPass.isEmpty()){
            confirmPassTet.error="Enter your confirm password"
            return false
        }
        if(userData.confirmPass!=userData.password){
            confirmPassTet.error="Passwords do not match"
            return false
        }
        if(rgGenderTet.checkedRadioButtonId==-1){
            Toast.makeText(this,"Choose Gender: Male or Female",
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }// End of Validation

    //register() Function
    private fun register() {
        val userData = getUserData()

        //Create volley string request
        val stringRequest = object : StringRequest(
            Request.Method.POST, BackendConfig.REGISTRATION_URL,
            Response.Listener<String> { response ->
                val trimmedResponse = response.trim()
                if (trimmedResponse == "true") {
                    Toast.makeText(
                        this,
                        "You are successfully registered",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(this, trimmedResponse, Toast.LENGTH_LONG).show()
                }
            },
            object : Response.ErrorListener {
                override fun onErrorResponse(volleyError: VolleyError) {
                    Toast.makeText(
                        applicationContext,
                        "Error: ${volleyError.message ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params.put("phpFunction", "createUser")
                params.put("fullName", userData.fullName)
                params.put("email", userData.email)
                params.put("password",userData.password)
                params.put("gender", userData.gender)
                params.put("age", userData.age)
                params.put("weight", userData.weight)
                params.put("height", userData.height)
                return params
            }
        }
        Volley.newRequestQueue(this).add(stringRequest)

    }

}//end of RegistrationActivity
