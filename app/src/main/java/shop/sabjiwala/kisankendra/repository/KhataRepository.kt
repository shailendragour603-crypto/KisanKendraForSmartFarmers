package shop.sabjiwala.kisankendra.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import shop.sabjiwala.kisankendra.model.CropCycle
import shop.sabjiwala.kisankendra.model.KhataItem

class KhataRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "KhataRepository"
    }

    fun addCropCycle(cycle: CropCycle, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        cycle.userId = uid

        val docRef = if (cycle.id.isNotBlank()) {
            firestore.collection("users").document(uid).collection("crop_cycles").document(cycle.id)
        } else {
            firestore.collection("users").document(uid).collection("crop_cycles").document()
        }

        cycle.id = docRef.id
        docRef.set(cycle)
            .addOnSuccessListener {
                Log.d(TAG, "CropCycle saved successfully to Firestore: ${cycle.id}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save CropCycle to Firestore: ${e.localizedMessage}")
                onComplete(false)
            }
    }

    fun updateCropCycle(cycle: CropCycle, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        if (cycle.id.isBlank()) return onComplete(false)

        firestore.collection("users").document(uid).collection("crop_cycles").document(cycle.id)
            .set(cycle)
            .addOnSuccessListener {
                Log.d(TAG, "CropCycle updated successfully in Firestore: ${cycle.id}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update CropCycle in Firestore: ${e.localizedMessage}")
                onComplete(false)
            }
    }

    fun deleteCropCycle(cycleId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        if (cycleId.isBlank()) return onComplete(false)

        firestore.collection("users").document(uid).collection("crop_cycles").document(cycleId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "CropCycle deleted from Firestore: $cycleId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete CropCycle from Firestore: ${e.localizedMessage}")
                onComplete(false)
            }
    }

    fun listenToCropCycles(onUpdate: (List<CropCycle>) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).collection("crop_cycles")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to CropCycles failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val cycles = snapshot.documents.mapNotNull { doc ->
                    try {
                        val cycle = doc.toObject(CropCycle::class.java)
                        if (cycle != null && cycle.id.isBlank()) {
                            cycle.id = doc.id
                        }
                        cycle
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse CropCycle doc ${doc.id}: ${e.localizedMessage}")
                        null
                    }
                }.sortedByDescending { it.timestamp }
                Log.d(TAG, "Fetched ${cycles.size} CropCycles from Firestore for UID: $uid")
                onUpdate(cycles)
            }
    }

    fun fetchCropCycles(onResult: (List<CropCycle>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(emptyList())
        firestore.collection("users").document(uid).collection("crop_cycles")
            .get()
            .addOnSuccessListener { snapshot ->
                val cycles = snapshot.documents.mapNotNull { doc ->
                    try {
                        val cycle = doc.toObject(CropCycle::class.java)
                        if (cycle != null && cycle.id.isBlank()) {
                            cycle.id = doc.id
                        }
                        cycle
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse CropCycle doc ${doc.id}: ${e.localizedMessage}")
                        null
                    }
                }.sortedByDescending { it.timestamp }
                Log.d(TAG, "Fetched ${cycles.size} CropCycles on-demand from Firestore for UID: $uid")
                onResult(cycles)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch CropCycles from Firestore: ${e.localizedMessage}")
                onResult(emptyList())
            }
    }

    fun addKhataItem(item: KhataItem, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        item.userId = uid

        val docRef = if (item.id.isNotBlank()) {
            firestore.collection("users").document(uid).collection("khata_entries").document(item.id)
        } else {
            firestore.collection("users").document(uid).collection("khata_entries").document()
        }

        item.id = docRef.id
        docRef.set(item)
            .addOnSuccessListener {
                Log.d(TAG, "KhataItem saved to Firestore: ${item.id}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save KhataItem to Firestore: ${e.localizedMessage}")
                onComplete(false)
            }
    }

    fun deleteKhataItem(itemId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        firestore.collection("users").document(uid).collection("khata_entries").document(itemId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "KhataItem deleted from Firestore: $itemId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete KhataItem from Firestore: ${e.localizedMessage}")
                onComplete(false)
            }
    }

    fun listenToKhataItems(onUpdate: (List<KhataItem>) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).collection("khata_entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen to KhataItems failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(KhataItem::class.java)
                        if (item != null && item.id.isBlank()) {
                            item.id = doc.id
                        }
                        item
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse KhataItem doc ${doc.id}: ${e.localizedMessage}")
                        null
                    }
                }.sortedByDescending { it.timestamp }
                Log.d(TAG, "Fetched ${items.size} KhataItems from Firestore for UID: $uid")
                onUpdate(items)
            }
    }

    fun fetchKhataItems(onResult: (List<KhataItem>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(emptyList())
        firestore.collection("users").document(uid).collection("khata_entries")
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(KhataItem::class.java)
                        if (item != null && item.id.isBlank()) {
                            item.id = doc.id
                        }
                        item
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse KhataItem doc ${doc.id}: ${e.localizedMessage}")
                        null
                    }
                }.sortedByDescending { it.timestamp }
                Log.d(TAG, "Fetched ${items.size} KhataItems on-demand from Firestore for UID: $uid")
                onResult(items)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch KhataItems from Firestore: ${e.localizedMessage}")
                onResult(emptyList())
            }
    }

    fun fetchPaginatedKhataItems(limit: Long, lastTimestamp: Long?, onResult: (List<KhataItem>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(emptyList())
        var query = firestore.collection("users").document(uid).collection("khata_entries")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)

        if (lastTimestamp != null) {
            query = query.startAfter(lastTimestamp)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(KhataItem::class.java)
                        if (item != null && item.id.isBlank()) {
                            item.id = doc.id
                        }
                        item
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse KhataItem doc ${doc.id}: ${e.localizedMessage}")
                        null
                    }
                }
                onResult(items)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch paginated KhataItems: ${e.localizedMessage}")
                onResult(emptyList())
            }
    }

    fun fetchKhataItemsByCycleIds(cycleIds: List<String>, onResult: (List<KhataItem>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(emptyList())
        if (cycleIds.isEmpty()) {
            onResult(emptyList())
            return
        }
        if (cycleIds.size <= 10) {
            firestore.collection("users").document(uid).collection("khata_entries")
                .whereIn("cropCycleId", cycleIds)
                .get()
                .addOnSuccessListener { snapshot ->
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            val item = doc.toObject(KhataItem::class.java)
                            if (item != null && item.id.isBlank()) item.id = doc.id
                            item
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    onResult(items)
                }
                .addOnFailureListener { e -> onResult(emptyList()) }
        } else {
            fetchKhataItems { items ->
                onResult(items.filter { it.cropCycleId in cycleIds })
            }
        }
    }
}
