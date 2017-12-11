package org.sputnikdev.bluetooth.manager.impl;

import org.sputnikdev.bluetooth.manager.BluetoothGovernor;

interface BluetoothObjectGovernor extends BluetoothGovernor {

    void update();

    void reset();

}
