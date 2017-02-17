package org.sputnikdev.bluetooth.manager.impl;

import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.impl.tinyb.TinyBAdapter;
import org.sputnikdev.bluetooth.manager.impl.tinyb.TinyBCharacteristic;
import org.sputnikdev.bluetooth.manager.impl.tinyb.TinyBDevice;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothType;

class TinyBFactory extends BluetoothObjectFactory {

    TinyBFactory() { }

    @Override Adapter getAdapter(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        return new TinyBAdapter(adapter);
    }

    @Override Device getDevice(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        BluetoothDevice device = (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.DEVICE, null, url.getDeviceAddress(), adapter);
        return new TinyBDevice(device);
    }

    @Override Characteristic getCharacteristic(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        BluetoothDevice device = (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.DEVICE, null, url.getDeviceAddress(), adapter);
        BluetoothGattService service = (BluetoothGattService) BluetoothManager.getBluetoothManager().getObject(
                        BluetoothType.GATT_SERVICE, null, url.getServiceUUID(), device);
        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic)
                BluetoothManager.getBluetoothManager().getObject(
                        BluetoothType.GATT_CHARACTERISTIC, null, url.getCharacteristicUUID(), service);
        return new TinyBCharacteristic(characteristic);
    }

}
