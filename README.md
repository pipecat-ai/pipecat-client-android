<h1><div align="center">
 <img alt="pipecat" width="500px" height="auto" src="assets/pipecat-android.png">
</div></h1>

# Pipecat Android Client SDK

[RTVI](https://github.com/rtvi-ai/) is an open standard for Real-Time Voice (and Video) Inference.

This Android library contains the core components and types needed to set up an RTVI session.

When building an RTVI application, you should use the transport-specific client library (see
[here](https://rtvi.mintlify.app/api-reference/transports/introduction) for available first-party
packages.) The base `RTVIClient` has no transport included.

## Usage

Add the following dependency to your `build.gradle` file:

```
implementation "ai.pipecat:client:0.3.3"
```

Then instantiate the `RTVIClient` from your code, specifying the backend `baseUrl` and transport.

```kotlin
val callbacks = object : RTVIEventCallbacks() {

    override fun onBackendError(message: String) {
        Log.e(TAG, "Error from backend: $message")
    }
    
    // ...
}

val client = RTVIClient(transport, callbacks, options)

client.start().withCallback {
    // ...
}
```

`client.start()` (and other APIs) return a `Future`, which can give callbacks, or be awaited
using Kotlin Coroutines (`client.start().await()`).
