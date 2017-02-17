package org.sputnikdev.bluetooth.manager.impl;

public interface Adapter<T> extends BluetoothObject<T> {

    String getName();

    String getAlias();
    void setAlias(String s);

    String getAddress();

    boolean isDiscovering();
    void enableDiscoveringNotifications(Notification<Boolean> notification);
    void disableDiscoveringNotifications();
    boolean startDiscovery();
    boolean stopDiscovery();

    boolean isPowered();
    void setPowered(boolean b);
    void enablePoweredNotifications(Notification<Boolean> notification);
    void disablePoweredNotifications();

}
