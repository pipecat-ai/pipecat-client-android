package ai.pipecat.client.types

/**
 * The current state of the session transport.
 */
enum class TransportState {
    Disconnected,
    Initializing,
    Initialized,
    Authorizing,
    Authorized,
    Connecting,
    Connected,
    Ready,
    Error
}