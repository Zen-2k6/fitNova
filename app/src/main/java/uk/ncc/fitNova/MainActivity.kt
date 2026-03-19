package uk.ncc.fitNova

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var userName: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        userName = findViewById(R.id.etUsername)
        password = findViewById(R.id.etPassword)

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)

        btnSignIn.setOnClickListener {
            if (validate()) {
                Login()
            }
        }

        btnSignUp.setOnClickListener {
            val intentSignUp = Intent(this, RegistrationActivity::class.java)
            startActivity(intentSignUp)
        }
    }

    private fun validate(): Boolean {
        if (userName.text.toString().trim().isEmpty()) {
            userName.error = "Enter Username"
            return false
        }

        if (password.text.toString().trim().isEmpty()) {
            password.error = "Enter Password"
            return false
        }

        return true
    }

    private fun Login() {

        val username = userName.text.toString().trim()
        val passwordText = password.text.toString().trim()

        val URL_Root = "http://10.0.2.2/rundao/LoginDAO.php"

        val stringRequest = object : StringRequest(
            Request.Method.POST, URL_Root,
            Response.Listener<String> { response ->

                try {
                    val obj = JSONObject(response)
                    val responseSuccess = obj.getString("response")

                    if (responseSuccess == "true") {

                        val userid = obj.getString("userid")
                        val fullName = obj.getString("FullName")
                        val weight = obj.getString("Weight")
                        val height = obj.getString("Height")

                        Toast.makeText(
                            this,
                            "Thank you for logging in $fullName",
                            Toast.LENGTH_SHORT
                        ).show()

                        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

                        with(sharedPref.edit()) {
                            putBoolean("IS_LOGGED_IN", true)
                            putInt("User_id", userid.toInt())
                            putString("Full_name", fullName)
                            putInt("Weight", weight.toInt())
                            putInt("Height", height.toInt())
                            apply()
                        }

                        val intentFitness =
                            Intent(this@MainActivity, ActivityFitness::class.java)
                        startActivity(intentFitness)

                    } else {
                        Toast.makeText(this, "Account does not exist", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: JSONException) {
                    e.printStackTrace()
                    Log.e("JSONError", "Failed to parse JSON: ${e.message}")
                    Toast.makeText(this@MainActivity, "JSON Error!", Toast.LENGTH_SHORT).show()
                }

            },
            Response.ErrorListener { error ->
                Log.e("VolleyError", "Error: ${error}")
                Toast.makeText(this@MainActivity, "Network Error!", Toast.LENGTH_SHORT).show()
            }
        ) {

            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String> {

                val params = HashMap<String, String>()
                params["phpFunction"] = "login"
                params["username"] = username
                params["password"] = passwordText

                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }
}