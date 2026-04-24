package com.myownbank.terminal

import com.google.firebase.database.FirebaseDatabase
import kotlin.random.Random

object TerminalManager {

    private val db = FirebaseDatabase.getInstance().reference

    fun generateUniqueId(callback: (String) -> Unit) {
        tryGenerate(callback)
    }

    private fun tryGenerate(callback: (String) -> Unit) {

        val id = "T" + (10000000..99999999).random().toString()

        db.child("terminals").child(id)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    callback(id)
                } else {
                    tryGenerate(callback)
                }
            }
    }
}