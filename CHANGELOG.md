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
