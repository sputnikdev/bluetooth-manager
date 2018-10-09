package org.sputnikdev.bluetooth.manager;

public enum ConnectionMethod {

    /**
     * Keeps disconnected state.
     */
    DISCONNECTED,
    /**
     * Keeps connected state.
     */
    CONNECTED,
    /**
     * Connects only when required, instantly disconnects after interaction is done.
     */
    EAGER,
    /**
     * Connects only when required, disconnects after a user defined timeout.
     */
    LAZY

}
