const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

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

    const { scribbleId } = event.params;
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
  }
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
  }
);
