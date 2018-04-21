package org.sputnikdev.bluetooth.manager.auth;

import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;

@FunctionalInterface
public interface AuthenticationProvider {

    void authenticate(BluetoothManager bluetoothManager, DeviceGovernor governor);

}
