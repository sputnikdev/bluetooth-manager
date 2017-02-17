package org.sputnikdev.bluetooth.manager;

import java.util.List;

public interface BluetoothSmartDeviceListener {

    void connected();
    void disconnected();

    void servicesResolved(List<GattService> gattServices);
    void servicesUnresolved();

}
