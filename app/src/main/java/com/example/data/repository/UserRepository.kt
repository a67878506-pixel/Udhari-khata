package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class UserIdLookupResult(
    val uid: String = "",
    val userId: String = "",
    val name: String = ""
)

sealed interface UserIdCheckState {
    data object Idle : UserIdCheckState
    data object Checking : UserIdCheckState
    data class Available(val handle: String) : UserIdCheckState
    data class Taken(val handle: String) : UserIdCheckState
    data class Invalid(val reason: String) : UserIdCheckState
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data object NotFound : ProfileState
    data class Exists(val user: User) : ProfileState
    data class Error(val message: String) : ProfileState
}

class UserRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance(db.app)

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfileState(uid: String): Flow<ProfileState> {
        val path = "users/$uid"
        return db.collection("users").document(uid).snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null && user.userId.isNotBlank()) {
                        ProfileState.Exists(user)
                    } else {
                        ProfileState.NotFound
                    }
                } else {
                    ProfileState.NotFound
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                emit(ProfileState.Error(error.localizedMessage ?: "Failed to load profile"))
            }
    }

    fun observeUserProfile(uid: String): Flow<User?> {
        val path = "users/$uid"
        return db.collection("users").document(uid).snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(User::class.java)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun checkUserIdAvailability(rawUserId: String): UserIdCheckState {
        val normalized = rawUserId.trim().lowercase()
        if (normalized.length < 3) {
            return UserIdCheckState.Invalid("User ID must be at least 3 characters")
        }
        if (normalized.length > 30) {
            return UserIdCheckState.Invalid("User ID cannot exceed 30 characters")
        }
        if (!normalized.matches(Regex("^[a-z0-9_.]+$"))) {
            return UserIdCheckState.Invalid("Letters, numbers, underscores, and dots only")
        }

        return try {
            val doc = db.collection("userIds").document(normalized).get().await()
            if (doc.exists()) {
                val currentUid = auth.currentUser?.uid
                val ownerUid = doc.getString("uid")
                if (ownerUid == currentUid) {
                    UserIdCheckState.Available(normalized)
                } else {
                    UserIdCheckState.Taken(normalized)
                }
            } else {
                UserIdCheckState.Available(normalized)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "userIds/$normalized")
            UserIdCheckState.Invalid("Failed to check availability: ${e.localizedMessage}")
        }
    }

    suspend fun registerUserProfile(name: String, email: String, chosenUserId: String): Result<User> {
        val uid = requireUserId()
        val normalized = chosenUserId.trim().lowercase()
        val cleanUserId = chosenUserId.trim()

        return try {
            val registeredUser = db.runTransaction { transaction ->
                val userRef = db.collection("users").document(uid)
                val userSnapshot = transaction.get(userRef)

                // If user profile already exists, preserve all existing data and do NOT overwrite
                if (userSnapshot.exists()) {
                    val existingUser = userSnapshot.toObject(User::class.java)
                    if (existingUser != null && existingUser.userId.isNotBlank()) {
                        return@runTransaction existingUser
                    }
                }

                val handleRef = db.collection("userIds").document(normalized)
                val handleSnapshot = transaction.get(handleRef)
                if (handleSnapshot.exists()) {
                    val existingUid = handleSnapshot.getString("uid")
                    if (existingUid != uid) {
                        throw IllegalStateException("User ID @$cleanUserId is already taken")
                    }
                }

                val handleData = mapOf(
                    "uid" to uid,
                    "userId" to cleanUserId,
                    "name" to name.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(handleRef, handleData)

                val userData = mapOf(
                    "uid" to uid,
                    "name" to name.trim(),
                    "email" to email.trim(),
                    "userId" to cleanUserId,
                    "normalizedUserId" to normalized,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(userRef, userData)

                User(
                    uid = uid,
                    name = name.trim(),
                    email = email.trim(),
                    userId = cleanUserId,
                    normalizedUserId = normalized
                )
            }.await()

            Result.success(registeredUser)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun searchUserByHandle(handle: String): Result<UserIdLookupResult?> {
        val normalized = handle.trim().lowercase().removePrefix("@")
        if (normalized.length < 3) return Result.success(null)

        return try {
            val doc = db.collection("userIds").document(normalized).get().await()
            if (doc.exists()) {
                Result.success(
                    UserIdLookupResult(
                        uid = doc.getString("uid") ?: "",
                        userId = doc.getString("userId") ?: normalized,
                        name = doc.getString("name") ?: "User"
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "userIds/$normalized")
            Result.failure(e)
        }
    }
}
