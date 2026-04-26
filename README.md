# VerifAI Android SDK

Zero-Knowledge Device Trust for Android.

## Installation

Add JitPack repository to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.Omriak10:verifai-android-sdk:1.0.4'
}
```

## Quick Start

### 1. Initialize

```kotlin
// In your Application class or MainActivity
VerifAI.init(context, "vf_live_your_api_key")
```

### 2. Register Device (First Login)

```kotlin
val result = VerifAI.register("user@email.com")
if (result.success) {
    // Device registered, proceed to app
}
```

### 3. Verify Device (Subsequent Logins)

```kotlin
val result = VerifAI.verify("user@email.com")

when (result.status) {
    VerifAI.Status.TRUSTED -> {
        // Allow access
        println("Trust score: ${result.trustScore}")
    }
    VerifAI.Status.NEW_DEVICE -> {
        // Show "waiting for approval" UI
        // User must approve from their primary device
        pollForApproval(result.sessionId!!)
    }
    VerifAI.Status.REJECTED -> {
        // Block access
        showError(result.error)
    }
}
```

### 4. Handle Push Notifications (For Approving Other Devices)

```kotlin
class MyFirebaseService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val approval = VerifAI.handlePushNotification(message.data)
        
        if (approval != null) {
            // Show approval dialog
            if (VerifAI.isSameNetwork(approval.publicIP)) {
                // Same network - safe to show approval
                showApprovalDialog(approval)
            } else {
                // Different network - auto-reject
                VerifAI.rejectDevice(approval.sessionId, reason = "different_network")
            }
        }
    }
}
```

## API Reference

### Initialization

```kotlin
VerifAI.init(
    context = applicationContext,
    apiKey = "vf_live_xxx",
    options = VerifAI.Options(
        enablePlayIntegrity = true,  // default: true
        timeoutSeconds = 30          // default: 30
    )
)
```

### Methods

| Method | Description |
|--------|-------------|
| `register(userId)` | Register device for first-time user |
| `verify(userId)` | Verify device on login |
| `getTrustScore(userId)` | Get current trust level |
| `registerPushToken(userId, token)` | Register FCM token |
| `listDevices(userId)` | List all trusted devices |
| `removeDevice(deviceId, type)` | Remove a trusted device |
| `approveDevice(sessionId, type)` | Approve pending device |
| `rejectDevice(sessionId, type, reason)` | Reject pending device |
| `isSameNetwork(remoteIP)` | Check network proximity |
| `handlePushNotification(data)` | Parse approval request from FCM |

### Trust Levels

| Level | Login Count | Score |
|-------|-------------|-------|
| BASELINE | 1 | 50 |
| MEDIUM | 2-4 | 70 |
| HIGH | 5-9 | 85 |
| VERY_HIGH | 10+ | 95 |

## Requirements

- Android API 24+
- Firebase project with Firestore
- Play Integrity API enabled

## License

Proprietary - Contact VerifAI for licensing.
