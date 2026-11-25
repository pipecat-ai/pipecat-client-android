---
name: update-rtvi
description: Bring the Android SDK up to a new RTVI protocol version that Pipecat released, from syncing its AsyncAPI document to handling every change it brings
---

Bring the SDK up to a new version of the RTVI protocol. Pipecat publishes an AsyncAPI document for each protocol version; the SDK keeps the one for the version it speaks and generates its protocol types from it. This skill syncs the new document, lists what changed, and carries each change through the SDK the way the existing messages are handled.

`AGENTS.md` describes the document, the generator and the tests that check the SDK against it. Read its "Protocol document" and "Protocol tests" entries first.

## Arguments

```
/update-rtvi [VERSION]
```

- `VERSION` (optional): the protocol version to move to, such as `2.4.0`. Without it, use the newest version in Pipecat's `released.json`.

## Instructions

### Step 1: Find the version

Read the protocol versions Pipecat has released, with the Pipecat release that first shipped each:

```bash
curl -s https://raw.githubusercontent.com/pipecat-ai/pipecat/main/src/pipecat/processors/frameworks/rtvi/spec/released.json
```

Compare with `RTVI_PROTOCOL_VERSION` in `pipecat-client-android/src/main/java/ai/pipecat/client/PipecatClient.kt`. When the SDK already speaks the newest version, or `VERSION`, stop and say so.

A version that isn't in `released.json` hasn't shipped, and its document can still change: only move to it if the developer asks, syncing from `main`.

### Step 2: Sync the document

Keep the current document for the comparison in Step 3, then bump the version and sync from the Pipecat release that shipped it (its `pipecat` value, as a tag: `1.18.0` is `v1.18.0`):

```bash
cp spec/<old>/rtvi.asyncapi.json /tmp/rtvi-<old>.json
# set RTVI_PROTOCOL_VERSION in PipecatClient.kt to <new>
python3 scripts/rtvi_protocol.py sync --ref v<pipecat release>
git rm -r spec/<old>
```

The sync writes `spec/<new>/rtvi.asyncapi.json` and regenerates `pipecat-client-android/src/main/java/ai/pipecat/client/types/RTVIProtocol.kt`. The SDK keeps only the document of the version it speaks.

When the generator stops on a schema it doesn't support, extend `scripts/rtvi_protocol.py` rather than working around it, keeping it to Python 3.9 and the standard library.

### Step 3: List what changed

Compare the two documents and list each change a client sees, by kind:

- **Messages** added or removed, by direction (`receiveClientMessages` is what clients send, `sendServerMessages` what the server sends).
- **Fields** added, removed, or made optional or required; enum and `const` values added.
- **Deprecations**: a message's `x-rtvi-deprecated`, a field's `deprecated: true`.
- **Gating**: a message's `x-rtvi-min-client-version` and `x-rtvi-enabled-by`.
- **Behaviors**: entries of `x-rtvi-behaviors` added or changed.

A short script that loads both documents and compares `components.messages`, `components.schemas` and `x-rtvi-behaviors` is the reliable way; don't eyeball a diff of the JSON. Also read `git diff` of the generated file: a renamed or retyped public property, or a reordered constructor, is a source break for apps. Show the list to the developer before editing anything.

### Step 4: Let the tests point at the work

```bash
./gradlew :pipecat-client-android:testDebugUnitTest
```

`RTVIDocumentTests` fails for each server message the SDK doesn't hand to the app, for each message type it names that the document doesn't have, and for each message it sends that no longer matches its schema. A compile error names each hand-written type or use of a generated type that the new types no longer fit.

Every failure should be on the Step 3 list. One that isn't means the list missed a change: go back to Step 3.

### Step 5: Carry each change through

Follow the code that already handles the same kind of change.

**A new server message.** Agree on its callback's name and data type with the developer first; they're public API. Name it as client-js names its callback, in the SDK's casing (`onBotLLMMarker` for client-js's `onBotLlmMarker`). Then add:

- its constant in `MsgServerToClient.Type`, in `transport/MsgServerToClient.kt`;
- its `open` callback, with an empty default, in `PipecatEventCallbacks.kt`. Its KDoc says what the message means, and, for a message with `x-rtvi-enabled-by`, that the bot sends it only when enabled, naming the setting. `onUserAudioLevel(data: AudioLevelData)` and `onSystemLog` are examples;
- its branch in the `when` of `PipecatClient`'s `onMessage`, decoding the generated type, or a public alias of it in `types/TextMessages.kt` when the generated name doesn't say what the message is (`SystemLog = TextMessageData`);
- its recording in `src/test/java/ai/pipecat/client/support/RecordingCallbacks.kt`.

To leave a message out instead, list it in `ignored` in `RTVIDocumentTests` with the reason, only when the developer decides so.

**A new client message.** Agree on the public method with the developer first. A bot older than the new version doesn't accept it, so the method fails with `RTVIError.UnsupportedFeature` for one, checking `unavailable(feature, major, minor)` as `sendFile` does. Add its builder to `transport/MsgClientToServer.kt`, and add the type it takes to `PUBLIC_CLIENT_TYPES` in the generator when apps build it. Test both cases, and send it in `messagesTheClientSendsMatchTheirSchemas`, which checks it against its schema; take it off `notSent`.

**A new field in an existing message.** A generated type has it already. A hand-written one (`HAND_WRITTEN` in the generator, such as `BotOutputData`) gets it added, with its description from the document. A field only newer bots send is optional, defaulting to `null`, since older bots leave it out; add it after the existing parameters, so apps' positional calls keep compiling.

**A field the new version removes or tightens.** Older bots still send the old form, so the type keeps reading it: a hand-written type with a serializer that fills it from the old field, as `BotOutputDataSerializer` fills `output_type` from `aggregated_by` and `text_type`. Move the type to `HAND_WRITTEN` when it was generated.

**A renamed type or property.** Keep the SDK's public name with `TYPE_NAMES` or `FIELD_NAMES`, or keep the old name as a deprecated `typealias` or property of the new one, so apps keep compiling. A nested class can't be aliased: deprecate it instead.

**A deprecated message or field.** Mark the callback and the property `@Deprecated`, naming the replacement, and add `@Suppress("DEPRECATION")` where the client calls it. Give the `MsgServerToClient.Type` constant a "Deprecated:" note in its KDoc instead, since the client's own `when` uses it. Keep handling it: older bots still send it.

**A new or changed behavior.** Read its description and check the client code that relies on it, such as `ResponseWaiters` for answers carrying a request's `id`. Change the code only when the behavior asks something new of the client.

### Step 6: Verify

```bash
python3 scripts/rtvi_protocol.py check
./gradlew :pipecat-client-android:testDebugUnitTest
./gradlew :pipecat-client-android:assembleRelease
```

The build should add no warnings: a deprecation warning means a deprecated callback or type is used without `@Suppress("DEPRECATION")`.

When a test fails, find out what it means before changing it. In particular, don't move a message into `ignored`, `notInDocument` or `notSent` to make a test pass.

### Step 7: Changelog and summary

Add the changes under `# Unreleased` in `CHANGELOG.md`: each new callback or method under `### Added`, each deprecation under `### Deprecated`, and each source break under `### Changed`, marked ⚠️ with what an app changes.

Then report, for the PR:

```markdown
## RTVI protocol <old> → <new>

- **Changes**: <the Step 3 list>.
- **Android SDK**: <each change and how the SDK handles it: new callbacks, new methods, fields, deprecations>.
- **Left out**: <messages added to `ignored`, with the developer's reason, or "none">.
```

## Guardrails

- Never edit `spec/` or `types/RTVIProtocol.kt` by hand: sync and regenerate.
- Never drop support for older bots: their fields, messages and fallbacks stay.
- Never add public API (a callback, method or type) without the developer agreeing to its name and shape.
- Never move a message into `ignored`, `notInDocument` or `notSent` without the developer's decision and a stated reason.

## Checklist

- [ ] The version was found in Pipecat's `released.json`, and the document synced from the release that shipped it
- [ ] Every change between the two documents was listed by kind before any edit
- [ ] Every test failure and compile error matched a listed change
- [ ] Each new server message has its constant, callback with a default, `onMessage` branch and recording
- [ ] Each new client message has a method that checks the bot's version, and is checked against its schema
- [ ] Deprecations are marked, with their replacement, and older bots still work
- [ ] The generated file is up to date, the tests pass, and the build has no warnings
- [ ] The changelog and the summary are written
