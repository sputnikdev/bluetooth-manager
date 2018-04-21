package org.sputnikdev.bluetooth.manager.auth;

public class BluetoothAuthenticationException extends RuntimeException {

    public BluetoothAuthenticationException() { }

    public BluetoothAuthenticationException(String message) {
        super(message);
    }

    public BluetoothAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

}
