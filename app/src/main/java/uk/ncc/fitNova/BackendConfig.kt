package uk.ncc.fitNova

object BackendConfig {
    private const val BASE_URL = "http://localhost:8000/FitNova"

    const val LOGIN_URL = "$BASE_URL/LoginDAO.php"
    const val REGISTRATION_URL = "$BASE_URL/RegistrationDAO.php"
}
