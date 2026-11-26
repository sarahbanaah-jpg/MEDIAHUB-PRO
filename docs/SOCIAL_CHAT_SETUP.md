# MEDIAHUB-PRO v5.0.0 social messaging backend setup

The Android app contains the messaging UI and client integrations. The following Firebase services must be deployed to make cloud chat, media uploads, push notifications and MediaHub AI work between devices.

## Firebase project
Use the existing project: `mediahub-pro-11393`.

## 1. Enable Firebase Storage
In Firebase Console, open **Build → Storage** and create the default bucket in your chosen region. Then deploy the repository's Storage rules.

## 2. Store the OpenAI key safely
Do not paste the OpenAI key into Kotlin, Gradle, GitHub source files, or the APK. Open a terminal in this repository and run:

```bash
firebase use mediahub-pro-11393
firebase functions:secrets:set OPENAI_API_KEY
```

Paste the key into the terminal's secure prompt (not into this chat). The key is read only by the server-side callable function through Google Secret Manager.

## 3. Deploy backend and security rules

```bash
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,storage,functions
```

Cloud Functions deployment requires the Firebase project to be on the Blaze billing plan and a linked billing account. OpenAI API usage is billed separately by OpenAI. The callable AI endpoint applies a basic limit of 20 requests per user per hour.

## 4. Chat behavior and security
- One-to-one chat messages are stored in Cloud Firestore.
- Photo and voice attachments are stored in Firebase Storage and require chat membership to read.
- FCM data messages trigger best-effort Android notifications with inline Reply and Mark as read actions.
- Android 13+ asks for notification permission. If the user denies it, Android will not display notifications.
- View-once/twice protected media uses Android `FLAG_SECURE`; this blocks normal screenshots and screen recording on supported Android builds, but cannot stop an external camera or a modified device.
- Disappearing messages are hidden by the app after their expiry timestamp. Configure a Firestore TTL policy on `chats/{chatId}/messages.expiresAt` if automatic server-side document cleanup is required.
- Chat content is **not end-to-end encrypted**. Firebase transport encryption and authenticated Firestore/Storage rules are used. Firebase project operators and authorized backend code can access message data.

## 5. Calling
The current chat UI writes a voice/video call invitation record to Firestore. It does not yet carry live microphone/camera media. Production calling requires a maintained WebRTC media stack and TURN relay credentials/service; those are not present in the current Firebase project configuration.

## 6. Test
1. Install the same signed APK on two Android devices.
2. Sign in with two different MEDIAHUB-PRO accounts.
3. Open Home → Messages on both devices.
4. Send text, a photo and a voice recording.
5. Test delete-for-me, delete-for-everyone, view-once/twice and disappearing timers.
6. Send a message while the recipient app is backgrounded; test inline Reply and Mark as read.
7. Open a chat and tap the ✦ AI button after the OpenAI secret and functions are deployed.
