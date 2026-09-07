const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue, Timestamp} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

// ── Email transporter (configure via Firebase Functions config or env vars) ──
// Set these using: firebase functions:secrets:set SMTP_USER / SMTP_PASS
// Or use a service like SendGrid / Resend instead of Gmail.
const createTransporter = () => nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.SMTP_USER, // e.g. sketchly.noreply@gmail.com
    pass: process.env.SMTP_PASS, // App Password (not account password)
  },
});

initializeApp();

/**
 * onScribbleCreate — FCM fan-out (Architecture §7)
 *
 * Triggered when a new Scribble document is created in `scribbles/{scribbleId}`.
 * For each recipientId in the document:
 *   1. Looks up the recipient's `fcmToken` from their `users/{uid}` document.
 *   2. Sends an FCM *data* message (not a notification message) so the client's
 *      ScribbleMessagingService can handle it silently and trigger a widget update.
 *
 * FCM data payload:
 *   { type: "new_scribble", scribbleId: "<id>", senderId: "<uid>" }
 *
 * Why data-only (not notification)?
 *   - Gives the client full control over how/when to surface the notification.
 *   - ScribbleMessagingService handles it in background, enqueuing WidgetUpdateWorker.
 *   - Avoids duplicate system-tray notifications when the app is foregrounded.
 */
exports.onScribbleCreate = onDocumentCreated(
  {
    document: "scribbles/{scribbleId}",
    region: "us-central1",
  },
  async (event) => {
    const scribble = event.data?.data();
    if (!scribble) {
      console.warn("onScribbleCreate: no data in snapshot, skipping.");
      return;
    }

    const {scribbleId} = event.params;
    const senderId = scribble.senderId;
    const recipientIds = scribble.recipientIds ?? [];

    if (!Array.isArray(recipientIds) || recipientIds.length === 0) {
      console.log(`onScribbleCreate: no recipients for scribble ${scribbleId}`);
      return;
    }

    const db = getFirestore();
    const messaging = getMessaging();

    // Fan-out: one FCM message per recipient (bounded to 10 by business rule — SRS)
    const sendPromises = recipientIds.map(async (recipientId) => {
      try {
        const userDoc = await db.collection("users").doc(recipientId).get();
        const fcmToken = userDoc.data()?.fcmToken;

        if (!fcmToken) {
          console.log(`onScribbleCreate: no FCM token for user ${recipientId}, skipping.`);
          return;
        }

        const message = {
          token: fcmToken,
          data: {
            type: "new_scribble",
            scribbleId: scribbleId,
            senderId: senderId,
          },
          android: {
            priority: "high", // Wake the device immediately
          },
        };

        await messaging.send(message);
        console.log(`onScribbleCreate: FCM sent to ${recipientId} for scribble ${scribbleId}`);
      } catch (err) {
        console.error(`onScribbleCreate: failed to send FCM to ${recipientId}:`, err);
        // Non-fatal — log and continue with remaining recipients
      }
    });

    await Promise.allSettled(sendPromises);
    console.log(`onScribbleCreate: fan-out complete for scribble ${scribbleId}`);
  },
);

/**
 * onReactionCreate — notify the Scribble sender when someone reacts (Architecture §7).
 *
 * Triggered on any write to `reactions/{reactionId}` (create or update to handle
 * reaction changes — SRS FR-7.2: "a user may change their reaction").
 *
 * Flow:
 *   1. Extract scribbleId, reactorId, reactorName, and emoji from the reaction doc.
 *   2. Look up the parent Scribble to find the senderId.
 *   3. Skip if the reactor IS the sender (reacting to your own note).
 *   4. Fetch the sender's fcmToken from users/{senderId}.
 *   5. Send an FCM notification message (visible alert) to the sender.
 *
 * Why notification (not data) here?
 *   - Reactions are a social nudge for the sender — they SHOULD see a visible
 *     notification in their system tray when backgrounded (SRS FR-7.3).
 */
exports.onReactionCreate = onDocumentWritten(
  {
    document: "reactions/{reactionId}",
    region: "us-central1",
  },
  async (event) => {
    // Use afterData (post-write state); skip on delete
    const reaction = event.data?.after?.data();
    if (!reaction) {
      console.log("onReactionCreate: document deleted or no data, skipping.");
      return;
    }

    const scribbleId = reaction.scribbleId;
    const reactorId = reaction.userId;
    const reactorName = reaction.userName || "Someone";
    const emoji = reaction.emoji;

    if (!scribbleId || !reactorId || !emoji) {
      console.warn("onReactionCreate: missing required fields, skipping.");
      return;
    }

    const db = getFirestore();
    const messaging = getMessaging();

    // Fetch the parent Scribble to get the senderId
    let senderId;
    try {
      const scribbleDoc = await db.collection("scribbles").doc(scribbleId).get();
      if (!scribbleDoc.exists) {
        console.warn(`onReactionCreate: scribble ${scribbleId} not found, skipping.`);
        return;
      }
      senderId = scribbleDoc.data()?.senderId;
    } catch (err) {
      console.error("onReactionCreate: failed to fetch scribble:", err);
      return;
    }

    // Edge case: skip if the reactor is the sender (reacting to their own note)
    if (reactorId === senderId) {
      console.log("onReactionCreate: reactor is sender, skipping self-notification.");
      return;
    }

    // Fetch the sender's FCM token
    let fcmToken;
    try {
      const senderDoc = await db.collection("users").doc(senderId).get();
      fcmToken = senderDoc.data()?.fcmToken;
    } catch (err) {
      console.error("onReactionCreate: failed to fetch sender FCM token:", err);
      return;
    }

    if (!fcmToken) {
      console.log(`onReactionCreate: no FCM token for sender ${senderId}, skipping.`);
      return;
    }

    // Send a visible notification to the sender
    try {
      const message = {
        token: fcmToken,
        notification: {
          title: "New reaction!",
          body: `${reactorName} reacted ${emoji} to your Scribble`,
        },
        data: {
          type: "reaction",
          scribbleId: scribbleId,
          emoji: emoji,
          reactorId: reactorId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "reactions", // Client must create this notification channel
          },
        },
      };
      await messaging.send(message);
      console.log(`onReactionCreate: reaction notification sent to sender ${senderId}`);
    } catch (err) {
      console.error("onReactionCreate: failed to send reaction FCM:", err);
    }
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// sendEmailOtp — generate and email a 6-digit OTP
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callable: sendEmailOtp({ email })
 *
 * 1. Generates a cryptographically random 6-digit OTP.
 * 2. Hashes it with SHA-256 and stores { hash, expiresAt } in
 *    Firestore under `emailOtps/{email}`.
 * 3. Sends the plaintext OTP to the user's email via nodemailer.
 *
 * Rate limit: prevents re-sending if a non-expired OTP already exists
 * (client should wait for countdown to finish before calling resend).
 */
exports.sendEmailOtp = onCall(
  {region: "us-central1", enforceAppCheck: false, invoker: "public"},
  async (request) => {
    const email = request.data?.email;
    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      throw new HttpsError("invalid-argument", "A valid email address is required.");
    }

    const db = getFirestore();
    const otpRef = db.collection("emailOtps").doc(email);

    // Check for a still-valid OTP (optional rate-limiting)
    const existing = await otpRef.get();
    if (existing.exists) {
      const {expiresAt} = existing.data();
      if (expiresAt && expiresAt.toMillis() > Date.now()) {
        // OTP still valid — don't spam. Client should use resend countdown.
        console.log(`sendEmailOtp: valid OTP already exists for ${email}, skipping resend.`);
        return {success: true};
      }
    }

    // Generate 6-digit OTP
    const otp = String(Math.floor(100000 + crypto.randomInt(900000))).padStart(6, "0");
    const hash = crypto.createHash("sha256").update(otp).digest("hex");
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10-minute TTL

    // Store hashed OTP in Firestore
    await otpRef.set({
      hash,
      expiresAt: FieldValue.serverTimestamp(),
      expiresAtMs: expiresAt.getTime(),
      createdAt: FieldValue.serverTimestamp(),
    });

    // Send email
    const transporter = createTransporter();
    await transporter.sendMail({
      from: `"Sketchly" <${process.env.SMTP_USER}>`,
      to: email,
      subject: "Your Sketchly verification code",
      html: `
        <div style="font-family: 'Georgia', serif; max-width: 480px; margin: auto; padding: 32px;">
          <h2 style="color: #34293F; font-style: italic; font-size: 28px; margin-bottom: 8px;">Enter your code</h2>
          <p style="color: #8A7F6C; font-size: 15px;">Use the code below to verify your Sketchly account.</p>
          <div style="
            background: #F7F2E4;
            border: 1px solid #D9CEAF;
            border-radius: 16px;
            padding: 24px;
            text-align: center;
            margin: 24px 0;
          ">
            <span style="
              font-size: 40px;
              font-weight: bold;
              letter-spacing: 12px;
              color: #2A2420;
            ">${otp}</span>
          </div>
          <p style="color: #8A7F6C; font-size: 13px;">
            This code expires in <strong>10 minutes</strong>. If you didn't request this, you can safely ignore this email.
          </p>
        </div>
      `,
    });

    console.log(`sendEmailOtp: OTP sent to ${email}`);
    return {success: true};
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// verifyEmailOtp — check the entered OTP against the stored hash
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callable: verifyEmailOtp({ email, otp })
 *
 * 1. Looks up the `emailOtps/{email}` document.
 * 2. Checks the OTP hasn't expired.
 * 3. Compares SHA-256(otp) to the stored hash.
 * 4. On success, deletes the OTP document (one-time use).
 *
 * Throws HttpsError on failure so the Android client receives a typed error.
 */
exports.verifyEmailOtp = onCall(
  {region: "us-central1", enforceAppCheck: false, invoker: "public"},
  async (request) => {
    const {email, otp} = request.data ?? {};
    if (!email || !otp) {
      throw new HttpsError("invalid-argument", "email and otp are required.");
    }

    const db = getFirestore();
    const otpRef = db.collection("emailOtps").doc(email);
    const doc = await otpRef.get();

    if (!doc.exists) {
      throw new HttpsError("not-found", "No OTP found for this email. Please request a new code.");
    }

    const {hash, expiresAtMs} = doc.data();

    // Expiry check
    if (!expiresAtMs || Date.now() > expiresAtMs) {
      await otpRef.delete();
      throw new HttpsError("deadline-exceeded", "This code has expired. Please request a new one.");
    }

    // Hash comparison (timing-safe)
    const inputHash = crypto.createHash("sha256").update(String(otp)).digest("hex");
    const isValid = crypto.timingSafeEqual(
      Buffer.from(inputHash, "hex"),
      Buffer.from(hash, "hex"),
    );

    if (!isValid) {
      throw new HttpsError("invalid-argument", "Incorrect code. Please try again.");
    }

    // One-time use: delete OTP document
    await otpRef.delete();

    console.log(`verifyEmailOtp: OTP verified for ${email}`);
    return {success: true};
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// matchContactsByHash — contact sync callable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Callable: matchContactsByHash({ hashes: string[] })
 *
 * Receives SHA-256 hashed phone numbers from the Android client,
 * matches them against registered users in Firestore, and returns
 * ONLY public profile fields of matched users.
 *
 * Security:
 *  1. Firebase Auth required — unauthenticated calls rejected.
 *  2. Rate limiting — max 5 syncs per user per 24-hour window.
 *  3. Batch size cap — max 500 hashes per call.
 *  4. Hash format validated (lowercase SHA-256, 64 hex chars).
 *  5. Phone hashes are NEVER returned to the client.
 *  6. Caller is excluded from their own results.
 *  7. Only users with isSearchable = true are returned.
 */
exports.matchContactsByHash = onCall(
  {region: "us-central1", enforceAppCheck: false, invoker: "public"},
  async (request) => {
    // ── 1. Auth check ──────────────────────────────────────────
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "You must be signed in to sync contacts.",
      );
    }
    const callerUid = request.auth.uid;

    // ── 2. Input validation ─────────────────────────────────────
    const hashes = request.data?.hashes;

    if (!Array.isArray(hashes)) {
      throw new HttpsError("invalid-argument", "hashes must be an array.");
    }

    if (hashes.length === 0) {
      return {matches: []};
    }

    if (hashes.length > 500) {
      throw new HttpsError(
        "invalid-argument",
        "Maximum 500 hashes per request.",
      );
    }

    // Validate each entry is a lowercase SHA-256 hex string (64 chars)
    const validHashPattern = /^[a-f0-9]{64}$/;
    const invalidHash = hashes.find((h) => !validHashPattern.test(h));
    if (invalidHash) {
      throw new HttpsError(
        "invalid-argument",
        "All hashes must be valid SHA-256 hex strings.",
      );
    }

    const db = getFirestore();

    // ── 3. Rate limiting (max 5 syncs per user per 24 h) ────────
    const rateLimitRef = db
      .collection("rateLimits")
      .doc(`contactSync_${callerUid}`);

    const rateLimitDoc = await rateLimitRef.get();
    const now = Date.now();
    const oneDayMs = 24 * 60 * 60 * 1000;

    if (rateLimitDoc.exists) {
      const {count, windowStart} = rateLimitDoc.data();
      if (now - windowStart < oneDayMs && count >= 5) {
        throw new HttpsError(
          "resource-exhausted",
          "Contact sync limit reached. Try again in 24 hours.",
        );
      }
      if (now - windowStart < oneDayMs) {
        await rateLimitRef.update({count: count + 1});
      } else {
        // New day — reset window
        await rateLimitRef.set({count: 1, windowStart: now});
      }
    } else {
      // First sync ever
      await rateLimitRef.set({count: 1, windowStart: now});
    }

    // ── 4. Query Firestore in chunks of 30 (whereIn limit) ──────
    const CHUNK_SIZE = 30;
    const chunks = [];
    for (let i = 0; i < hashes.length; i += CHUNK_SIZE) {
      chunks.push(hashes.slice(i, i + CHUNK_SIZE));
    }

    const queryPromises = chunks.map((chunk) =>
      db
        .collection("users")
        .where("phoneNumberHash", "in", chunk)
        .where("isSearchable", "==", true)
        // Only return public fields — hash is intentionally omitted
        .select("uid", "displayName", "username", "avatarUrl", "phoneLastFour")
        .get(),
    );

    const queryResults = await Promise.all(queryPromises);

    // ── 5. Assemble deduplicated results ─────────────────────────
    const matches = [];
    const seenUids = new Set();

    queryResults.forEach((snapshot) => {
      snapshot.docs.forEach((doc) => {
        const userData = doc.data();
        const uid = userData.uid || doc.id;

        // Exclude caller from their own results
        if (uid === callerUid) return;

        // Deduplicate (a number may appear multiple times in contacts)
        if (seenUids.has(uid)) return;
        seenUids.add(uid);

        matches.push({
          uid,
          displayName: userData.displayName || "Sketchly User",
          username: userData.username || "",
          avatarUrl: userData.avatarUrl || null,
          phoneLastFour: userData.phoneLastFour || null,
          // phoneNumberHash intentionally excluded
        });
      });
    });

    // ── 6. Return ─────────────────────────────────────────────────
    console.log(
      `matchContactsByHash: caller=${callerUid}, hashes=${hashes.length}, matches=${matches.length}`,
    );

    return {matches};
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// onFollowRequestCreate — notify the target user of a new follow request
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Triggered when a new document is created in `followRequests/{requestId}`.
 *
 * Expected doc shape:
 *   { fromUserId, toUserId, fromUserName, status: "pending", createdAt }
 *
 * Flow:
 *   1. Read fromUserId, toUserId, fromUserName from the new doc.
 *   2. Fetch toUser's fcmToken from users/{toUserId}.
 *   3. Send a visible FCM notification to the target user.
 */
exports.onFollowRequestCreate = onDocumentCreated(
  {
    document: "followRequests/{requestId}",
    region: "us-central1",
  },
  async (event) => {
    const request = event.data?.data();
    if (!request) {
      console.warn("onFollowRequestCreate: no data in snapshot, skipping.");
      return;
    }

    const {fromUserId, toUserId, fromUserName} = request;

    if (!fromUserId || !toUserId) {
      console.warn("onFollowRequestCreate: missing fromUserId or toUserId, skipping.");
      return;
    }

    const db = getFirestore();
    const messaging = getMessaging();

    // Fetch the target user's FCM token
    let fcmToken;
    try {
      const toUserDoc = await db.collection("users").doc(toUserId).get();
      fcmToken = toUserDoc.data()?.fcmToken;
    } catch (err) {
      console.error("onFollowRequestCreate: failed to fetch target FCM token:", err);
      return;
    }

    if (!fcmToken) {
      console.log(`onFollowRequestCreate: no FCM token for user ${toUserId}, skipping.`);
      return;
    }

    try {
      const senderName = fromUserName || "Someone";
      await messaging.send({
        token: fcmToken,
        notification: {
          title: "New follow request",
          body: `${senderName} wants to follow you on Sketchly`,
        },
        data: {
          type: "follow_request",
          fromUserId,
          requestId: event.params.requestId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "social", // Client must create this notification channel
          },
        },
      });
      console.log(`onFollowRequestCreate: notification sent to ${toUserId}`);
    } catch (err) {
      console.error("onFollowRequestCreate: failed to send FCM:", err);
    }
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// onFollowRequestAccept — create connection docs for both users + notify sender
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Triggered on any write to `followRequests/{requestId}`.
 * Only acts when `status` transitions to "accepted".
 *
 * Flow:
 *   1. Confirm status changed from non-accepted → "accepted".
 *   2. Create `connections/{fromUserId}_{toUserId}` and the reverse doc.
 *   3. Send FCM notification to fromUserId (request sender) to let them know
 *      their request was accepted.
 */
exports.onFollowRequestAccept = onDocumentWritten(
  {
    document: "followRequests/{requestId}",
    region: "us-central1",
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    // Guard: only proceed when status flips to "accepted"
    if (!after || after.status !== "accepted") return;
    if (before?.status === "accepted") return; // Already processed

    const {fromUserId, toUserId} = after;

    if (!fromUserId || !toUserId) {
      console.warn("onFollowRequestAccept: missing user IDs, skipping.");
      return;
    }

    const db = getFirestore();
    const messaging = getMessaging();
    const now = FieldValue.serverTimestamp();

    // ── Create bidirectional connection docs ──────────────────────
    const connectionId = `${fromUserId}_${toUserId}`;
    const reverseId = `${toUserId}_${fromUserId}`;
    const connectionsRef = db.collection("connections");

    try {
      const [fromDoc, toDoc] = await Promise.all([
        db.collection("users").doc(fromUserId).get(),
        db.collection("users").doc(toUserId).get(),
      ]);
      const fromData = fromDoc.exists ? fromDoc.data() : {};
      const toData = toDoc.exists ? toDoc.data() : {};

      const batch = db.batch();
      batch.set(connectionsRef.doc(connectionId), {
        userAId: fromUserId,
        userBId: toUserId,
        userId: toUserId,
        displayName: toData.displayName || "Sketchly User",
        username: toData.username || "",
        avatarUrl: toData.avatarUrl || null,
        source: "FOLLOW_REQUEST",
        connectionStatus: "CONNECTED",
        createdAt: now,
      });
      batch.set(connectionsRef.doc(reverseId), {
        userAId: toUserId,
        userBId: fromUserId,
        userId: fromUserId,
        displayName: fromData.displayName || "Sketchly User",
        username: fromData.username || "",
        avatarUrl: fromData.avatarUrl || null,
        source: "FOLLOW_REQUEST",
        connectionStatus: "CONNECTED",
        createdAt: now,
      });
      await batch.commit();
      console.log(`onFollowRequestAccept: connections created for ${fromUserId} ↔ ${toUserId}`);
    } catch (err) {
      console.error("onFollowRequestAccept: failed to create connection docs:", err);
      // Continue to send notification even if Firestore write partially failed
    }

    // ── Notify the requester that their follow was accepted ───────
    let fcmToken;
    try {
      const fromUserDoc = await db.collection("users").doc(fromUserId).get();
      fcmToken = fromUserDoc.data()?.fcmToken;
    } catch (err) {
      console.error("onFollowRequestAccept: failed to fetch requester FCM token:", err);
      return;
    }

    if (!fcmToken) {
      console.log(`onFollowRequestAccept: no FCM token for ${fromUserId}, skipping notification.`);
      return;
    }

    try {
      const acceptorName = after.toUserName || "Someone";
      await messaging.send({
        token: fcmToken,
        notification: {
          title: "Follow request accepted",
          body: `${acceptorName} accepted your follow request`,
        },
        data: {
          type: "follow_accepted",
          toUserId,
          requestId: event.params.requestId,
        },
        android: {
          priority: "normal",
          notification: {
            channelId: "social",
          },
        },
      });
      console.log(`onFollowRequestAccept: acceptance notification sent to ${fromUserId}`);
    } catch (err) {
      console.error("onFollowRequestAccept: failed to send acceptance FCM:", err);
    }
  },
);

// ─────────────────────────────────────────────────────────────────────────────
// purgeInactiveData — scheduled monthly data retention cleanup (Architecture §10)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Runs on the 1st of every month at 03:00 UTC.
 *
 * Cleans up:
 *  1. `emailOtps` — expired OTP documents (expiresAtMs < now).
 *  2. `rateLimits` — contactSync rate-limit windows older than 30 days.
 *  3. `followRequests` — declined/cancelled requests older than 90 days.
 *
 * Each collection is processed in batches of 500 to stay within Firestore
 * batch-write limits.
 */
exports.purgeInactiveData = onSchedule(
  {
    schedule: "0 3 1 * *", // 1st of every month at 03:00 UTC
    timeZone: "UTC",
    region: "us-central1",
  },
  async (_event) => {
    const db = getFirestore();
    const now = Date.now();
    const BATCH_SIZE = 500;

    let totalDeleted = 0;

    /**
     * Helper: delete all docs returned by a query, in batches of BATCH_SIZE.
     * @param {FirebaseFirestore.Query} query - Firestore query to delete from.
     * @param {string} label - Label for log output.
     * @return {Promise<number>} Number of deleted documents.
     */
    async function batchDelete(query, label) {
      let deleted = 0;
      let snapshot = await query.limit(BATCH_SIZE).get();

      while (!snapshot.empty) {
        const batch = db.batch();
        snapshot.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();
        deleted += snapshot.size;
        if (snapshot.size < BATCH_SIZE) break;
        snapshot = await query.limit(BATCH_SIZE).get();
      }

      console.log(`purgeInactiveData [${label}]: deleted ${deleted} docs`);
      return deleted;
    }

    // ── 1. Expired email OTPs ──────────────────────────────────────
    totalDeleted += await batchDelete(
      db.collection("emailOtps").where("expiresAtMs", "<", now),
      "emailOtps",
    );

    // ── 2. Stale rate-limit windows (older than 30 days) ──────────
    const thirtyDaysAgo = now - 30 * 24 * 60 * 60 * 1000;
    totalDeleted += await batchDelete(
      db.collection("rateLimits").where("windowStart", "<", thirtyDaysAgo),
      "rateLimits",
    );

    // ── 3. Old declined/cancelled follow requests (90+ days) ──────
    const ninetyDaysAgo = Timestamp.fromMillis(now - 90 * 24 * 60 * 60 * 1000);
    totalDeleted += await batchDelete(
      db
        .collection("followRequests")
        .where("status", "in", ["declined", "cancelled"])
        .where("createdAt", "<", ninetyDaysAgo),
      "followRequests",
    );

    console.log(`purgeInactiveData: total docs deleted = ${totalDeleted}`);
  },
);
