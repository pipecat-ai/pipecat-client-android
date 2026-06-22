# Unreleased

- Added support for RTVI protocol 2.0.0. `BotOutputData` now includes new fields: `willBeSpoken`, `spokenStatus`, `spokenProgress`, and `segmentId`, enabling word-level TTS progress tracking. The `spoken` field is deprecated in favour of `willBeSpoken`.
- Added `RTVI_PROTOCOL_VERSION` constant (`"2.0.0"`).
- The client now logs a warning when the bot is running an older RTVI protocol version than the client.

# 1.1.0

- Update signature of `Transport.deserializeConnectParams()` to also take the startBot `APIRequest`.

- Implemented support for the new `botOutput` RTVI message. This message is now the preferred
  way of communicating a holistic view of what the bot "says". It includes a `spoken` field,
  indicating whether the text has been spoken along with a field, `aggregated_by`, to indicate what
  the text represents. By default, with TTS services that support word-by-word output, you can
  expect two `agggregated_by` values for `botOutput` events: `"sentence"` and `"word"`. All
  sentence events are guaranteed to be in order, while word events come in at the time of being
  spoken. This allows for building karaoke-like UIs where the sentence is displayed and each word
  is highlighted as it's spoken.  This event also provides continuity across bot output even when
  the TTS is skipped or does not exist. And if your pipeline takes advantage of customizing how
  the LLM text is aggregated, you can handle custom `aggregated_by` fields, like `"code"` or
  `"address"` or `"url"`, allowing the server to do the parsing.

# 1.0.2

- Added new `sendText()` method to support the new RTVI `send-text` event. The method
  takes a string, along with an optional set of options to control whether the bot
  should respond immediately and/or whether the bot should respond with audio (vs. text only).
  Note: This is a replacement for the current `appendToContext()` method.

- `appendToContext()` has been deprecated in favor of the new `sendText()` method. 

# 1.0.1

- Changed content type `startBot()` POST request for compatibility reasons 

# 1.0.0

- Added:
  - Methods:
    - `startBot()`
    - `startBotAndConnect()`
    - `appendToContext()`
    - `sendClientMessage()`
    - `sendClientRequest()`
    - `registerFunctionCallHandler()`
    - `unregisterFunctionCallHandler()`
    - `unregisterAllFunctionCallHandlers()`
    - `disconnectBot()`
  - Callbacks:
    - `onBotLlmSearchResponse()`
    - `onLLMFunctionCall()`
- Removed:
  - Helper classes (`LLMHelper`, `RTVIClientHelper`) and associated methods (`registerHelper`, `unregisterHelper`)
  - Server configuration, including `getConfig()`, `updateConfig()`, `describeConfig()` and associated types
  - Actions, including `action()`, `describeActions()` and associated types
  - `expiry` field on client and transports
  - `onStorageItemStored()` callback
  - `RTVIClientParams`
  - `sendWithResponse()`
  - `sendMessage()`
- Changed:
  - `connect()` is modified to no longer make a POST request to the backend, but rather pass the
    specified `Value` straight to the `Transport`. See `startBotAndConnect()` for a helper method
    which also includes the POST request.
  - `Transport` now passed directly into `PipecatClientOptions` rather than using factory
  - `onBotReady()` now receives a `BotReadyData` parameter
- Renamed:
  - `RTVIClient` -> `PipecatClient`
  - `RTVIEventCallbacks` -> `PipecatEventCallbacks`
  - `RTVIClientOptions` -> `PipecatClientOptions`
  - `onPipecatMetrics()` -> `onMetrics()`

# 0.3.4

- Added `onServerMessage` callback

# 0.3.3

- Made `timestamp` and `userId` fields optional in `Transcript`

# 0.3.0

- Project renamed to `pipecat-client-android`

# 0.2.1

- Added callbacks:
  - onBotLLMStarted
  - onBotLLMStopped
  - onBotTTSStarted
  - onBotTTSStopped

# 0.2.0

- Renamed:
  - `VoiceClient` to `RTVIClient`
  - `VoiceClientOptions` to `RTVIClientOptions`
  - `VoiceEventCallbacks` to `RTVIEventCallbacks`
  - `VoiceError` to `RTVIError`
  - `VoiceException` to `RTVIException`
  - `VoiceClientHelper` to `RTVIClientHelper`
  - `RegisteredVoiceClient` to `RegisteredRTVIClient`
  - `FailedToFetchAuthBundle` to `HttpError`
- `RTVIClient()` constructor parameter changes
  - `options` is now mandatory
  - `baseUrl` has been moved to `options.params.baseUrl`
  - `baseUrl` and `endpoints` are now separate, and the endpoint names are appended to the `baseUrl`
- Moved `RTVIClientOptions.config` to `RTVIClientOptions.params.config`
- Moved `RTVIClientOptions.customHeaders` to `RTVIClientOptions.params.headers`
- Moved `RTVIClientOptions.customBodyParams` to `RTVIClientOptions.params.requestData`
- `TransportState` changes
  - Removed `Idle` state, replaced with `Disconnected`
  - Added `Disconnecting` state
- Added callbacks
  - `onBotLLMText()`
  - `onBotTTSText()`
  - `onStorageItemStored()`
