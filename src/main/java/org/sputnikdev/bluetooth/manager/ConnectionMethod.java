package org.sputnikdev.bluetooth.manager;

public enum ConnectionMethod {

    /**
     * Keeps disconnected state.
     */
    DISCONNECTED(false),
    /**
     * Keeps connected state.
     */
    CONNECTED(false),
    /**
     * Connects only when required, instantly disconnects after interaction is done.
     */
    EAGER(true),
    /**
     * Connects only when required, disconnects after a user defined timeout.
     */
    LAZY(true);


    private boolean onDemand;

    ConnectionMethod(boolean onDemand) {
        this.onDemand = onDemand;
    }

    public boolean isOnDemand() {
        return onDemand;
    }

}
