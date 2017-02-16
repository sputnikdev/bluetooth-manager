package org.sputnikdev.bluetooth.manager;

import java.util.Date;

public interface GenericBluetoothDeviceListener {

    void online();

    void offline();

    void blocked(boolean blocked);

    void rssiChanged(short rssi);

    void lastUpdatedChanged(Date lastActivity);

}
