package org.sputnikdev.bluetooth.manager;

/**
 * This exception happens during interactions with bluetooth governors that require a communication
 * with physical devices.
 *
 * @author Vlad Kolotov
 */
public class BluetoothInteractionException extends RuntimeException {

    public BluetoothInteractionException() { }

    public BluetoothInteractionException(String message) {
        super(message);
    }

    public BluetoothInteractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
