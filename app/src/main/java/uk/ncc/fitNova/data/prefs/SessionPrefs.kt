package uk.ncc.fitNova.data.prefs

import android.content.Context

data class SessionSnapshot(
    val isLoggedIn: Boolean = false,
    val userId: Int = -1,
    val fullName: String = "",
    val email: String = "",
    val gender: String = "",
    val age: Int = 0,
    val weight: Int = 0,
    val height: Int = 0
)

class SessionPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): SessionSnapshot {
        return SessionSnapshot(
            isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false),
            userId = prefs.getInt(KEY_USER_ID, -1),
            fullName = prefs.getString(KEY_FULL_NAME, "").orEmpty(),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            gender = prefs.getString(KEY_GENDER, "").orEmpty(),
            age = prefs.getInt(KEY_AGE, 0),
            weight = prefs.getInt(KEY_WEIGHT, 0),
            height = prefs.getInt(KEY_HEIGHT, 0)
        )
    }

    fun getUserId(): Int = read().userId

    fun getWeight(): Int = read().weight

    fun saveLogin(snapshot: SessionSnapshot) {
        save(snapshot.copy(isLoggedIn = true))
    }

    fun updateProfile(
        fullName: String,
        email: String,
        gender: String,
        age: Int,
        weight: Int,
        height: Int
    ) {
        val current = read()
        save(
            current.copy(
                fullName = fullName,
                email = email,
                gender = gender,
                age = age,
                weight = weight,
                height = height
            )
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun save(snapshot: SessionSnapshot) {
        with(prefs.edit()) {
            putBoolean(KEY_IS_LOGGED_IN, snapshot.isLoggedIn)
            putInt(KEY_USER_ID, snapshot.userId)
            putString(KEY_FULL_NAME, snapshot.fullName)
            putString(KEY_EMAIL, snapshot.email)
            putString(KEY_GENDER, snapshot.gender)
            putInt(KEY_AGE, snapshot.age)
            putInt(KEY_WEIGHT, snapshot.weight)
            putInt(KEY_HEIGHT, snapshot.height)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "UserPrefs"
        private const val KEY_IS_LOGGED_IN = "IS_LOGGED_IN"
        private const val KEY_USER_ID = "User_id"
        private const val KEY_FULL_NAME = "Full_name"
        private const val KEY_EMAIL = "Email"
        private const val KEY_GENDER = "Gender"
        private const val KEY_AGE = "Age"
        private const val KEY_WEIGHT = "Weight"
        private const val KEY_HEIGHT = "Height"
    }
}
