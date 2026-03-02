package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.model.LoginResponse
import com.example.myapplication.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailEdit = findViewById<EditText>(R.id.emailEditText)
        val passwordEdit = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                val lat = "37.4219983"
                val lng = "122.084"

                loginApi(email, password, lat, lng)
            }
        }
    }

    private fun loginApi(username: String, password: String, lat: String, lng: String) {
        val call = RetrofitClient.instance.login(username, password, lat, lng)
        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val loginData = response.body()!!
                    println("Check Token => ${loginData.Token}")
                    println("Check TrackingToken => ${loginData.TrackingToken}")
                    println("Check UserID => ${loginData.userid}")

                    val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("TOKEN", loginData.Token)
                        putString("TRACKING_TOKEN", loginData.TrackingToken)
                        putString("USER_ID", loginData.userid)
                        apply()
                    }

                    val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                    intent.putExtra("userid", loginData.userid)
                    startActivity(intent)
                    finish()
                } else {
                    println("Check error response => ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                println("Check failure => ${t.localizedMessage}")
            }
        })
    }
}