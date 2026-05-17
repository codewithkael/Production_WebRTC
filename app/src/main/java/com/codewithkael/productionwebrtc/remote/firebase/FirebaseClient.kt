package com.codewithkael.productionwebrtc.remote.firebase

import android.util.Log
import com.codewithkael.productionwebrtc.utils.MyApplication
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClient @Inject constructor(
    private val database: DatabaseReference, private val gson: Gson
) {
    //  Unify all coroutines into a single CoroutineScope
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val userId = MyApplication.UserID

    fun observeIncomingSignals(callback: (SignalDataModel) -> Unit) {
        database.child(FirebaseFieldNames.USERS).child(userId).child(FirebaseFieldNames.SIGNALS)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    runCatching {
                        gson.fromJson(snapshot.value.toString(), SignalDataModel::class.java)
                    }.onSuccess { signal ->
                        if (signal != null) {
                            callback(signal)
                            // Proactive cleanup: remove signal after receiving
                            snapshot.ref.removeValue()
                        }
                    }.onFailure {
                        Log.d(MyApplication.TAG, "onChildAdded error: ${it.message}")
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    suspend fun sendSignal(participantId: String, data: SignalDataModel) {
        database.child(FirebaseFieldNames.USERS).child(participantId).child(FirebaseFieldNames.SIGNALS)
            .push()
            .setValue(gson.toJson(data)).await()
    }

    suspend fun removeSelfData() {
        database.child(FirebaseFieldNames.USERS).child(userId).child(FirebaseFieldNames.SIGNALS)
            .removeValue().await()
    }

    // Cleanup function to cancel all running coroutines
    fun clear() {
        coroutineScope.cancel()
    }
}
