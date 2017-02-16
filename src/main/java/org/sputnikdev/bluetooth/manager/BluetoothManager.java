package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.manager.impl.tinyb.BluetoothSmartServiceImpl;

public class BluetoothManager {

    public static BluetoothSmartService getBluetoothSmartService() {
        return new BluetoothSmartServiceImpl();
    }

}
