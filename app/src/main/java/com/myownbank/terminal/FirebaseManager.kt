package com.myownbank.terminal

import com.google.firebase.database.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class FirebaseManager {

    private val db = FirebaseDatabase.getInstance().reference

    fun registerTerminal(terminalId: String) {

        val terminalRef = db.child("terminals").child(terminalId)

        terminalRef.setValue(
            mapOf(
                "status" to "online",
                "command" to "idle",
                "price" to 0
            )
        )

    }

    fun listenCommand(terminalId: String, callback: (String, Int) -> Unit) {

        db.child("terminals").child(terminalId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val command =
                        snapshot.child("command").getValue(String::class.java) ?: "idle"
                    val price =
                        snapshot.child("price").getValue(Int::class.java) ?: 0

                    callback(command, price)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun resetToIdle(terminalId: String) {
        db.child("terminals").child(terminalId).child("command").setValue("idle")
    }
}