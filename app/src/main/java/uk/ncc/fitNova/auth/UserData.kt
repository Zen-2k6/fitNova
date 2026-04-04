package uk.ncc.fitNova.auth

data class UserData(
    val fullName :String,
    val email:String,
    val password: String,
    val confirmPass:String,
    val age:String,
    val weight:String,
    val height:String,
    val gender: String

)
