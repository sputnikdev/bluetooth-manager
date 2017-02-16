package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.gattparser.URL;

public interface BluetoothSmartService {

    boolean isAdapterPowered(URL url);
    boolean getAdapterPoweredControl(URL url);
    void setAdapterPoweredControl(URL url, boolean power);

    boolean isAdapterDiscovering(URL url);
    boolean getAdapterDiscoveringControl(URL url);
    void setAdapterDiscoveringControl(URL url, boolean discovering);

    void setAdapterAlias(URL url, String alias);
    void addAdapterListener(URL url, AdapterListener adapterListener);
    void removeAdapterListener(URL url);

    void startDiscovery();
    void stopDiscovery();
    void addDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);
    void removeDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);

    boolean isConnected(URL url);
    boolean getConnectionControl(URL url);
    void setConnectionControl(URL url, boolean connected);

    boolean isBlocked(URL url);
    boolean getBlockedControl(URL url);
    void setBlockedControl(URL url, boolean blocked);

    boolean isOnline(URL url);

    void disposeBluetoothObject(URL url);

    short getRSSI(URL url);

    void addBluetoothSmartDeviceListener(URL url, BluetoothSmartDeviceListener listener);
    void removeBluetoothSmartDeviceListener(URL url, BluetoothSmartDeviceListener listener);

    void addGenericBluetoothDeviceListener(URL url, GenericBluetoothDeviceListener listener);
    void removeGenericBluetoothDeviceListener(URL url);

    byte[] readCharacteristic(URL url);
    void addCharacteristicListener(URL url, CharacteristicListener characteristicListener);
    void removeCharacteristicListener(URL url);

    boolean writeCharacteristic(URL url, byte[] data);

    void dispose();

}
