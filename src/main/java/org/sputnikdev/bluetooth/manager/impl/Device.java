package org.sputnikdev.bluetooth.manager.impl;

import java.util.List;


public interface Device<T> extends BluetoothObject<T> {

    boolean disconnect();

    boolean connect();

    String getAddress();

    String getName();

    String getAlias();

    void setAlias(String alias);

    boolean isBlocked();

    void enableBlockedNotifications(Notification<Boolean> notification);

    void disableBlockedNotifications();

    void setBlocked(boolean blocked);

    short getRSSI();

    void enableRSSINotifications(Notification<Short> notification);

    void disableRSSINotifications();

    boolean isConnected();

    void enableConnectedNotifications(Notification<Boolean> notification);

    void disableConnectedNotifications();

    boolean isServicesResolved();

    void enableServicesResolvedNotifications(Notification<Boolean> notification);

    void disableServicesResolvedNotifications();

    List<Service> getServices();

}
