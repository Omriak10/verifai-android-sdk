# VerifAI Android SDK

Zero-knowledge device trust for Android apps. Verify the device, not just the password.

## Install

Add JitPack to your project's `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.Omriak10:verifai-android-sdk:2.0.0'
}
```

## Quick start

```kotlin
// Initialize once
VerifAI.init(applicationContext, "vf_live_your_api_key")

// Register device (first login)
val result = VerifAI.register("user@example.com")

// Verify device (subsequent logins)
val result = VerifAI.verify("user@example.com")
when (result.status) {
    VerifAI.Status.TRUSTED -> { /* allow access */ }
    VerifAI.Status.NEW_DEVICE -> { /* show approval UI */ }
    VerifAI.Status.REJECTED -> { /* block access */ }
}
```

## What's new in v2.0.0

- **No Firebase dependency** — the SDK now talks to the VerifAI REST API directly. No `google-services.json` required in your app.
- **Full usage tracking** — every register and verify call is tracked via your API key.
- **Lighter footprint** — removed Firebase Firestore SDK dependency.

## API

| Method | Description |
|---|---|
| `VerifAI.init(context, apiKey)` | Initialize the SDK |
| `VerifAI.register(userId)` | Register a new device |
| `VerifAI.verify(userId)` | Verify device on login |
| `VerifAI.getTrustScore(userId)` | Get trust level + score |
| `VerifAI.listDevices(userId)` | List all trusted devices |
| `VerifAI.removeDevice(deviceId)` | Remove a trusted device |
| `VerifAI.registerPushToken(userId, token)` | Register FCM token |
| `VerifAI.approveDevice(sessionId)` | Approve a pending session |
| `VerifAI.rejectDevice(sessionId)` | Reject a pending session |
| `VerifAI.handlePushNotification(data)` | Parse FCM push data |
| `VerifAI.isSameNetwork(remoteIP)` | Compare public IPs |

## License

Proprietary. Contact VerifAI for licensing.
