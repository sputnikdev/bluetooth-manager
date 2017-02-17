package org.sputnikdev.bluetooth.manager.impl;

public interface Characteristic<T> extends BluetoothObject<T> {

    String getUUID();

    String[] getFlags();

    boolean isNotifying();

    void disableValueNotifications();

    byte[] readValue();

    boolean writeValue(byte[] data);

    void enableValueNotifications(Notification<byte[]> notification);
}
