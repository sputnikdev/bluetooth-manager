package org.sputnikdev.bluetooth.manager.impl;

import org.sputnikdev.bluetooth.gattparser.URL;

public abstract class BluetoothObjectFactory {

    BluetoothObjectFactory() { }

    static BluetoothObjectFactory getDefault() {
        return new TinyBFactory();
    }

    abstract Adapter getAdapter(URL url);

    abstract Device getDevice(URL url);

    abstract Characteristic getCharacteristic(URL url);

}
