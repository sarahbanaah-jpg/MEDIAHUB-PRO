const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");

/**
 * Delivers chat push notifications from trusted server code.
 * Notifications are best-effort: Android permission, device connectivity,
 * OEM battery policy and FCM delivery can affect display time.
 */
exports.notifyChatMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message || !message.receiverId || !message.senderId) return;
    const receiverId = String(message.receiverId);
    if (receiverId === String(message.senderId)) return;

    const [profile, tokenSnap] = await Promise.all([
      db.collection("public_profiles").doc(String(message.senderId)).get(),
      db.collection("users").doc(receiverId).collection("devices").get()
    ]);
    const tokens = tokenSnap.docs.map((d) => d.id).filter(Boolean);
    if (!tokens.length) return;

    const senderName = profile.get("displayName") || "MEDIAHUB-PRO user";
    const preview = message.type === "image" ? "Sent you a photo"
      : message.type === "voice" ? "Sent you a voice message"
      : message.type === "call" ? "Incoming call"
      : String(message.text || "New message").slice(0, 180);

    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        chatId: String(event.params.chatId),
        messageId: String(event.params.messageId),
        senderId: String(message.senderId),
        senderName: String(senderName).slice(0, 80),
        body: preview
      },
      android: { priority: "high" }
    });

    const invalid = [];
    response.responses.forEach((r, i) => {
      if (!r.success && ["messaging/registration-token-not-registered",
        "messaging/invalid-registration-token"].includes(r.error?.code)) {
        invalid.push(tokens[i]);
      }
    });
    if (invalid.length) {
      const batch = db.batch();
      invalid.forEach((token) => batch.delete(db.collection("users").doc(receiverId).collection("devices").doc(token)));
      await batch.commit();
    }
  }
);

/**
 * MediaHub AI chat helper. The OpenAI key is only read by this server function
 * from Secret Manager; it is never shipped in the APK.
 */
exports.chatAssistant = onCall(
  { secrets: [OPENAI_API_KEY], timeoutSeconds: 60, memory: "256MiB" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to use MediaHub AI.");
    const prompt = String(request.data?.prompt || "").trim();
    if (!prompt || prompt.length > 4000) {
      throw new HttpsError("invalid-argument", "Enter a prompt of up to 4,000 characters.");
    }

    // Basic per-user request throttling to limit accidental or abusive spend.
    const usageRef = db.collection("ai_usage").doc(request.auth.uid);
    const now = Date.now();
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(usageRef);
      const start = snap.get("windowStart") || 0;
      const count = start > now - 60 * 60 * 1000 ? (snap.get("count") || 0) : 0;
      if (count >= 20) throw new HttpsError("resource-exhausted", "AI limit reached. Try again later.");
      tx.set(usageRef, { windowStart: start > now - 60 * 60 * 1000 ? start : now, count: count + 1 });
    });

    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${OPENAI_API_KEY.value()}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "gpt-6.2-astra",
        instructions: "You are MediaHub AI, a concise, friendly assistant inside MEDIAHUB-PRO. Help with media, devices, music, video, app usage and general questions. Do not claim to perform actions you cannot perform.",
        input: prompt,
        max_output_tokens: 700,
        store: false
      })
    });

    if (!response.ok) {
      const detail = await response.text();
      console.error("OpenAI Responses API error", response.status, detail.slice(0, 500));
      throw new HttpsError("unavailable", "MediaHub AI is temporarily unavailable.");
    }
    const result = await response.json();
    let answer = result.output_text;
    if (!answer && Array.isArray(result.output)) {
      answer = result.output.flatMap((item) => item.content || [])
        .filter((part) => part.type === "output_text")
        .map((part) => part.text).join("\n");
    }
    return { answer: String(answer || "I couldn't generate a response.").slice(0, 6000) };
  }
);
