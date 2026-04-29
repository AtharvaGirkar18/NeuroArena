package com.example.neuroarenanavigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SecondActivity : AppCompatActivity() {

    private lateinit var tvDisplayData: TextView
    private lateinit var btnGoBack: Button
    private lateinit var btnOpenBrowser: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // Initialize views
        tvDisplayData = findViewById(R.id.tvDisplayData)
        btnGoBack = findViewById(R.id.btnGoBack)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)

        // RETRIEVE DATA USING getIntent() - Getting data passed from DashboardActivity
        val receivedData = intent.getStringExtra("USER_DATA") ?: "No data received"
        val userId = intent.getIntExtra("USER_ID", 0)

        // Display the received data
        tvDisplayData.text = "Received: $receivedData\nUser ID: $userId"

        // Toast to confirm data received
        Toast.makeText(this, "Data received successfully!", Toast.LENGTH_SHORT).show()

        // Go back to previous activity
        btnGoBack.setOnClickListener {
            finish() // This will go back to DashboardActivity
            Toast.makeText(this, "Going back to Dashboard", Toast.LENGTH_SHORT).show()
        }

        // IMPLICIT INTENT - Open Browser
        btnOpenBrowser.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            startActivity(browserIntent)
            Toast.makeText(this, "Opening Browser...", Toast.LENGTH_SHORT).show()
        }
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        println("SecondActivity: onStart")
    }

    override fun onResume() {
        super.onResume()
        println("SecondActivity: onResume")
    }

    override fun onPause() {
        super.onPause()
        println("SecondActivity: onPause")
    }

    override fun onStop() {
        super.onStop()
        println("SecondActivity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("SecondActivity: onDestroy")
    }
}