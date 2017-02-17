package org.sputnikdev.bluetooth.manager.impl;

import org.sputnikdev.bluetooth.manager.BluetoothManager;

public class BluetoothManagerFactory {

    private static BluetoothManager instance;

    private BluetoothManagerFactory() { }

    public static BluetoothManager getManager() {
        if (instance == null) {
            synchronized (BluetoothManager.class) {
                if (instance == null) {
                    instance = new BluetoothManagerImpl();
                }
            }
        }
        return instance;
    }

}
