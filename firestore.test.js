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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: denied access to sessions, notes, reminders, and settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/sessions`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/preferences/settings`).get());

  const past = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      title: "Hack",
      content: "Unauthorized",
      pinned: false,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("ChatSession & ChatMessage: owner create, update, query, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_1`).set({
      userId: ALICE_UID,
      title: "Morning Briefing",
      lastMessage: "Hello Jarvis",
      languageCode: "en",
      messageCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_1/messages/msg_1`).set({
      userId: ALICE_UID,
      sessionId: "sess_1",
      role: "user",
      text: "Hello Jarvis",
      hasImage: false,
      languageCode: "en",
      createdAt: created,
    })
  );

  // Partial session update succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_1`).update({
      title: "Updated Briefing",
      lastMessage: "Systems online, sir.",
      languageCode: "hi",
      messageCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner queries succeed
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sessions`).orderBy("updatedAt", "desc").get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sessions/sess_1/messages`).orderBy("createdAt", "asc").get()
  );

  // Reject ghost field `id` on create, future timestamp, invalid language, and message updates
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_2`).set({
      id: "sess_2",
      userId: ALICE_UID,
      title: "Bad",
      lastMessage: "",
      languageCode: "en",
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_1`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_1/messages/msg_1`).update({
      text: "Tampered",
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/sessions`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/sessions/sess_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/sessions/sess_1`).delete());
});

test("Notes & Reminders: owner CRUD, validation bounds, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).set({
      userId: ALICE_UID,
      title: "Arc Reactor Specs",
      content: "Palladium core replacement schedule.",
      pinned: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).update({
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reminders/rem_1`).set({
      userId: ALICE_UID,
      title: "Call Pepper",
      dueAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reminders/rem_1`).update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_2`).set({
      userId: ALICE_UID,
      title: "Future",
      content: "Bad",
      pinned: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/reminders/rem_1`).update({ completed: false }));
});

test("Preferences settings: owner write, bounds enforcement, and non-settings ID rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/preferences/settings`).set({
      userId: ALICE_UID,
      languageCode: "te",
      autoSpeak: true,
      speechRate: 1.0,
      speechPitch: 1.0,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/preferences/settings`).update({
      languageCode: "hi",
      speechRate: 1.25,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid language and out-of-range rate and non-'settings' doc ID
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/preferences/settings`).update({
      languageCode: "fr",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/preferences/settings`).update({
      speechRate: 5.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/preferences/other`).set({
      userId: ALICE_UID,
      languageCode: "en",
      autoSpeak: true,
      speechRate: 1.0,
      speechPitch: 1.0,
      updatedAt: past,
    })
  );
});
