package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KhataRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createKhata_authenticatedUser_createsDocumentAndReturnsId() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = KhataRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createKhata("Ghar ka Khata", "Alice")
        }
        if (result.isFailure) {
            throw AssertionError("createKhata failed with: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
        }
        val khataId = result.getOrThrow()
        assertTrue(khataId.isNotEmpty())
    }

    @Test
    fun observeKhatas_authenticatedOwner_returnsMatchingKhatas() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = KhataRepository(firestore)

        val khataId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createKhata("Business Khata", "Alice").getOrThrow()
        }

        val emittedKhatas = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserKhatas(uid).first { list ->
                list.any { it.khataId == khataId }
            }
        }
        assertTrue(emittedKhatas.map { it.khataId }.contains(khataId))
    }

    @Test
    fun addPersonAndTransaction_authenticatedOwner_updatesTotals() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val khataRepo = KhataRepository(firestore)
        val personRepo = PersonRepository(firestore)
        val txRepo = TransactionRepository(firestore)

        val khataId = khataRepo.createKhata("Shop Khata", "Alice").getOrThrow()
        val personId = personRepo.addPerson(khataId, "Ramesh", "9876543210", "Milkman", "Alice").getOrThrow()
        assertTrue(personId.isNotEmpty())

        val txId = txRepo.addTransaction(
            khataId = khataId,
            personId = personId,
            personName = "Ramesh",
            amount = 500L,
            type = TransactionType.LENA,
            note = "Doodh ka hisab",
            actorName = "Alice"
        ).getOrThrow()
        assertTrue(txId.isNotEmpty())

        val person = withTimeout(FLOW_TIMEOUT_MS) {
            personRepo.observePerson(khataId, personId).first { it != null && it.totalLena == 500L }
        }
        assertNotNull(person)
        assertEquals(500L, person?.totalLena)
        assertEquals(500L, person?.netBalance)
    }

    @Test
    fun observeKhatas_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = KhataRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserKhatas("unauthenticated_uid").first()
            }
            fail("Expected exception")
        } catch (e: Exception) {
            val firestoreException = (e as? FirebaseFirestoreException)
                ?: (e.cause as? FirebaseFirestoreException)
                ?: (e.cause?.cause as? FirebaseFirestoreException)
            assertNotNull(firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun userProfileFlow_firstTimeRegistrationAndReturningUser_preservesExactUserId() = runBlocking {
        val uid = signInTestUser("user_flow@khata.app")
        val userRepo = UserRepository(firestore)

        // 1. Initial state: Profile does not exist yet
        val initialProfile = userRepo.observeProfileState(uid).first()
        assertTrue(initialProfile is ProfileState.NotFound)

        // 2. First-time registration with unique handle
        val registeredResult = userRepo.registerUserProfile(
            name = "Rahul Sharma",
            email = "user_flow@khata.app",
            chosenUserId = "rahul_khata"
        )
        assertTrue(registeredResult.isSuccess)
        val user = registeredResult.getOrThrow()
        assertEquals("rahul_khata", user.userId)
        assertEquals("Rahul Sharma", user.name)

        // 3. Returning user check: Profile now exists with exact same User ID
        val returningState = userRepo.observeProfileState(uid).first()
        assertTrue(returningState is ProfileState.Exists)
        val returningUser = (returningState as ProfileState.Exists).user
        assertEquals("rahul_khata", returningUser.userId)

        // 4. Repeated registration attempt: Preserves existing data, does NOT overwrite
        val secondAttempt = userRepo.registerUserProfile(
            name = "Different Name",
            email = "user_flow@khata.app",
            chosenUserId = "different_handle"
        )
        assertTrue(secondAttempt.isSuccess)
        val preservedUser = secondAttempt.getOrThrow()
        // Kept original user ID and name!
        assertEquals("rahul_khata", preservedUser.userId)
        assertEquals("Rahul Sharma", preservedUser.name)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_test@khata.app"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
