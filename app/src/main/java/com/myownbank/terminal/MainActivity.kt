package com.myownbank.terminal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.myownbank.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var terminalId: String
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var isPaymentInProgress = false
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.terminalId.text = "Подключение..."
        binding.networkStatus.visibility = View.GONE

        networkMonitor = NetworkMonitor(this) { connected ->
            runOnUiThread {
                binding.networkStatus.visibility = if (connected) View.GONE else View.VISIBLE
            }
        }
        networkMonitor.start()

        binding.root.post {
            TerminalManager.generateUniqueId { id ->
                terminalId = id
                binding.terminalId.text = "ID терминала: $terminalId"

                val terminalRef = db.child("terminals").child(terminalId)

                terminalRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            terminalRef.setValue(
                                mapOf(
                                    "price" to 0L,
                                    "disabledPayment" to "",
                                    "status" to "idle",
                                    "command" to "idle",
                                    "failureChance" to 10,
                                    "failMessage" to "",
                                    "paymentTimeout" to 60
                                )
                            )
                        }
                        listenTerminalCommands(terminalRef)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("MainActivity", "Firebase error: ${error.message}")
                    }
                })

                // Глобальный статус приложения
                db.child("settings/admin").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val status = snapshot.child("appStatus").getValue(String::class.java) ?: "enabled"
                        if (status == "disabled") {
                            val intent = Intent(this@MainActivity, AppDisabledActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
        }
    }

    private fun listenTerminalCommands(terminalRef: DatabaseReference) {
        terminalRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val command = snapshot.child("command").getValue(String::class.java)?.lowercase() ?: "idle"
                val price = snapshot.child("price").getValue(Long::class.java) ?: 0L
                val failureChance = snapshot.child("failureChance").getValue(Int::class.java) ?: 10
                val failMessage = snapshot.child("failMessage").getValue(String::class.java) ?: ""
                val paymentTimeout = snapshot.child("paymentTimeout").getValue(Int::class.java) ?: 60

                if (command == "start" && !isPaymentInProgress) {
                    isPaymentInProgress = true
                    val intent = Intent(this@MainActivity, PaymentActivity::class.java).apply {
                        putExtra("price", price)
                        putExtra("terminalId", terminalId)
                        putExtra("failureChance", failureChance)
                        putExtra("failMessage", failMessage)
                        putExtra("paymentTimeout", paymentTimeout)
                    }
                    startActivity(intent)
                    terminalRef.child("command").setValue("idle")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Firebase cancelled: ${error.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        isPaymentInProgress = false
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stop()
    }
}
