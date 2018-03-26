package org.sputnikdev.bluetooth.manager;

/**
 * An exception that indicates an error that cannot be recovered from by the governor itself
 * so that higher level governor (adapter -> device -> characteristic) must take an action (reset itself).
 */
public class BluetoothFatalException extends RuntimeException  {

    public BluetoothFatalException() {

    }

    public BluetoothFatalException(String message) {
        super(message);
    }

    public BluetoothFatalException(String message, Throwable cause) {
        super(message, cause);
    }

}
