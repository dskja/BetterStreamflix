package com.betterstreamflix.architecture

/**
 * State machine — generic state machine for managing UI or feature
 * state transitions with validation.
 */
class StateMachine<S : Enum<S>, E : Enum<E>>(
    initialState: S,
    private val transitions: Map<Pair<S, E>, S>,
) {
    private var _state: S = initialState
    val state: S get() = _state

    private val listeners = mutableListOf<(S, S, E) -> Unit>()

    /**
     * Send an event to transition to a new state.
     * Returns true if the transition was valid.
     */
    fun send(event: E): Boolean {
        val key = _state to event
        val newState = transitions[key]
        if (newState == null || newState == _state) return false

        val oldState = _state
        _state = newState
        listeners.forEach { it(oldState, newState, event) }
        return true
    }

    /**
     * Check if an event can be sent from the current state.
     */
    fun canSend(event: E): Boolean {
        return transitions.containsKey(_state to event)
    }

    /**
     * Add a state change listener.
     */
    fun addListener(listener: (oldState: S, newState: S, event: E) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: (oldState: S, newState: S, event: E) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Reset to initial state.
     */
    fun reset(initialState: S) {
        _state = initialState
    }

    /**
     * Get all valid events from the current state.
     */
    fun validEvents(): List<E> {
        return transitions.keys.filter { it.first == _state }.map { it.second }
    }
}

/**
 * Builder for creating state machines.
 */
class StateMachineBuilder<S : Enum<S>, E : Enum<E>> {
    private val transitions = mutableMapOf<Pair<S, E>, S>()

    fun transition(from: S, event: E, to: S): StateMachineBuilder<S, E> {
        transitions[from to event] = to
        return this
    }

    fun build(initialState: S): StateMachine<S, E> {
        return StateMachine(initialState, transitions.toMap())
    }
}
