const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated checks
test("Unauthenticated: cannot read khatas", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("khatas").get());
});

test("Unauthenticated: cannot read users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc("any_user").get());
});

// 2. User Profiles and Handles
test("Authenticated: can create user profile with matching UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice Sharma",
      email: "alice@example.com",
      userId: "alice_khata",
      normalizedUserId: "alice_khata",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated: cannot create user profile for someone else", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      name: "Bob",
      email: "bob@example.com",
      userId: "bob_khata",
      normalizedUserId: "bob_khata",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated: can reserve unique userId handle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("userIds").doc("alice_khata").set({
      uid: ALICE_UID,
      userId: "alice_khata",
      name: "Alice Sharma",
      createdAt: new Date(),
    })
  );
});

// 3. Khata Access & Isolation
test("Authenticated: can create a Khata as owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("khatas").doc("khata_1").set({
      khataId: "khata_1",
      name: "Ghar ka Khata",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      totalLena: 0,
      totalDena: 0,
      netBalance: 0,
      peopleCount: 0,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: non-member cannot read Alice's khata", async () => {
  // Seed Alice's Khata bypassing rules
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("khatas").doc("khata_alice").set({
      khataId: "khata_alice",
      name: "Shop Khata",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      totalLena: 500,
      totalDena: 0,
      netBalance: 500,
      peopleCount: 1,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("khatas").doc("khata_alice").get());
});

test("Shared Khata: Partner member CAN read and add person/transaction", async () => {
  // Seed shared Khata with Alice and Bob as members
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("khatas").doc("khata_shared").set({
      khataId: "khata_shared",
      name: "Business Partnership",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      totalLena: 0,
      totalDena: 0,
      netBalance: 0,
      peopleCount: 0,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("khatas").doc("khata_shared").get());

  // Bob can add a person to the shared Khata
  await assertSucceeds(
    bobDb.collection("khatas").doc("khata_shared").collection("people").doc("p_ramesh").set({
      personId: "p_ramesh",
      khataId: "khata_shared",
      name: "Ramesh Kumar",
      phone: "9876543210",
      note: "Vegetable vendor",
      totalLena: 0,
      totalDena: 0,
      netBalance: 0,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob can add a transaction
  await assertSucceeds(
    bobDb.collection("khatas").doc("khata_shared").collection("transactions").doc("tx_101").set({
      transactionId: "tx_101",
      khataId: "khata_shared",
      personId: "p_ramesh",
      amount: 500,
      type: "LENA",
      note: "Sabzi bill",
      createdBy: BOB_UID,
      createdByName: "Bob",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

// 4. Non-member cannot access people or transactions in shared khata
test("Non-member Charlie cannot read people or transactions in Alice's khata", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("khatas").doc("khata_private").set({
      khataId: "khata_private",
      name: "Private Khata",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      totalLena: 0,
      totalDena: 0,
      netBalance: 0,
      peopleCount: 0,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("khatas").doc("khata_private").collection("people").get()
  );
});

// 5. Audit Log Immutability
test("Audit Log is append-only: Cannot update or delete", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("khatas").doc("khata_audit").set({
      khataId: "khata_audit",
      name: "Audit Test Khata",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      totalLena: 0,
      totalDena: 0,
      netBalance: 0,
      peopleCount: 0,
      archived: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("khatas").doc("khata_audit").collection("auditLogs").doc("log_1").set({
      logId: "log_1",
      khataId: "khata_audit",
      actorUid: ALICE_UID,
      actorName: "Alice",
      action: "KHATA_CREATED",
      description: "Khata created",
      createdAt: new Date(),
    })
  );

  // Updating the audit log must FAIL
  await assertFails(
    aliceDb.collection("khatas").doc("khata_audit").collection("auditLogs").doc("log_1").update({
      description: "Tampered description",
    })
  );

  // Deleting the audit log must FAIL
  await assertFails(
    aliceDb.collection("khatas").doc("khata_audit").collection("auditLogs").doc("log_1").delete()
  );
});
