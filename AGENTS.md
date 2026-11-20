# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Repository

Android library `ai.pipecat:client`, the Android client SDK for [Pipecat](https://github.com/pipecat-ai/pipecat): the client side of the RTVI protocol the Pipecat server speaks. It's a Gradle project whose library module is `pipecat-client-android`, published to Maven.

Concrete transports (Daily, SmallWebRTC, Gemini Live, OpenAI Realtime) are **not** in this repo; each lives in its own repo and extends the `Transport` class from this library. A change to `Transport` reaches them only once this library is released.

## Commands

```bash
# Unit tests (JUnit and Robolectric, on the JVM)
./gradlew :pipecat-client-android:testDebugUnitTest

# Build the library
./gradlew :pipecat-client-android:assembleRelease

# RTVI protocol document: copy it from Pipecat's main (or --ref <tag>, or --pipecat <checkout>) and regenerate
python3 scripts/rtvi_protocol.py sync
python3 scripts/rtvi_protocol.py generate   # regenerate only
python3 scripts/rtvi_protocol.py check      # fails when the generated file is out of date
```

- Gradle needs Java 17.
- The script runs on macOS's own `/usr/bin/python3` (3.9), with no dependencies: keep it to the standard library and 3.9's syntax. CI runs `check` in a `python:3.9` container.
- `minSdk` is 24: use `android.util.Base64`, not `java.util.Base64`.

## Commits, changelog, releases

- Commit messages are plain sentences ending with a period, not Conventional Commits.
- `CHANGELOG.md` has an entry per release, with `### Added`, `### Changed`, `### Deprecated`, `### Removed` and `### Fixed` sections. Add changes under `# Unreleased`; a breaking change is marked ⚠️ and says what an app changes.
- A release sets the publication's `version` in `pipecat-client-android/build.gradle.kts` and names the changelog entry after it.

## Architecture

- **`PipecatClient`** (`PipecatClient.kt`) is the public entry point. It starts the bot (`startBot`, a `POST` to the app's endpoint), connects the transport, and sends client messages (`sendText`, `sendFile`, `sendDTMF`, `sendClientMessage`, `sendClientRequest`, `disconnectBot`). Its work runs on a `ThreadRef`, the thread that created it, which needs a `Looper`; public methods return a `Future` from `ai.pipecat.client.result`. The `TransportContext`'s `onMessage` is a `when` over `MsgServerToClient.Type` that decodes each server message and calls the callbacks.
- **`PipecatEventCallbacks`** (`PipecatEventCallbacks.kt`) has a method for each thing the app hears about, each `open` with an empty default so apps override only what they use (`onBackendError` is the only abstract one). The transport gets the app's callbacks through `TransportContext.callbacks` and calls the connection, participant, device, track and local audio level methods directly, and forwards every RTVI message to `TransportContext.onMessage`. So the client doesn't see those calls: state it keeps, such as the bot's delivery, is reset from messages, from `disconnect()` and from `onConnectionEnd`.
- **`Transport`** (`transport/Transport.kt`) is the abstract class transports extend: devices, connect and disconnect, `sendMessage`, tracks. A new member is `open` with a default, as `maxMessageSize` is, so the transports in other repos keep compiling. Transports send `client-ready` themselves, with `TransportContext.protocolVersion`.
- **`ResponseWaiters`** (`utils/ResponseWaiters.kt`) implements request and response over RTVI: it keeps each request by its id, and settles it on the `server-response` or `error-response` carrying that id.
- **Protocol document**: `spec/<version>/rtvi.asyncapi.json` is Pipecat's AsyncAPI document of the RTVI protocol, for the version `RTVI_PROTOCOL_VERSION` in `PipecatClient.kt` names, and `types/RTVIProtocol.kt` holds the types `scripts/rtvi_protocol.py` generates from it. Neither is edited by hand. The script's tables at its top decide what isn't generated (`SKIPPED_MESSAGES`, and `HAND_WRITTEN` types such as `BotOutputData`, which also read fields older bots send), the public names that differ from the schema's (`TYPE_NAMES`, `FIELD_NAMES`, `ENUM_NAMES`), and which client-message types apps build (`PUBLIC_CLIENT_TYPES`; other client-message types are internal). Enums are generated as `@JvmInline value class`es of constants, so a value the client doesn't name still decodes; unions are sealed interfaces with a serializer that tries each member. Public aliases name a generated type after a message, as `LLMRawText` and `SystemLog` are aliases of `TextMessageData` (`types/TextMessages.kt`).
- **Protocol tests**: `RTVIDocumentTests` checks the client against the document: every server message reaches the callbacks or is listed in `ignored` with a reason, each example reaches them with all of its data (also with a field the client doesn't know), every message type the client names is in the document or listed in `notInDocument`, and every message the client sends matches its schema, with the client messages it never sends listed in `notSent`. A new message in the document fails these until it's handled or listed. The `update-rtvi` skill carries a new protocol version that Pipecat released through the SDK.
- Behavior that depends on the bot's protocol version reads `botVersion`, parsed from `bot-ready`, through `botSupports(major, minor)`; `unavailable()` turns a missing version into `RTVIError.UnsupportedFeature`. `sendFile` needs 2.2.0, and `sendDTMF` sends 2.0.x bots one message per key. A file whose message is larger than `Transport.maxMessageSize` is uploaded first, to the `fileUploadUrl` the start-bot response advertised or `PipecatClientOptions.fileUploadEndpoint`, and sent by its URL.
- Deprecated callbacks and fields stay, marked `@Deprecated` with their replacement, and the client keeps calling them, with `@Suppress("DEPRECATION")` where it does: older bots still send the messages behind them. A deprecated `MsgServerToClient.Type` constant gets a "Deprecated:" note in its KDoc instead, since the client's own `when` uses it. Kotlin can't alias a nested class, so a nested type replaced by a generated one is deprecated rather than aliased, as `MsgServerToClient.Data.BotLLMTextData` is.

## Tests

- `src/test/java/ai/pipecat/client/support/` holds the harness: `ClientTestCase` makes a client on a `TransportStub`, which records what the client sends and hands it messages as JSON; `RecordingCallbacks` records each callback with its data; `Json.kt` compares and validates messages as they are on the wire. HTTP requests, such as uploads, go to OkHttp's `MockWebServer`.
- The client runs its work on the main `Looper`, which Robolectric holds until it's run: call `idle()`, or `await(future)`, before checking what was sent or called.
- A new callback needs its recording in `RecordingCallbacks`; without it, `RTVIDocumentTests` reports the message as not reaching the app.
