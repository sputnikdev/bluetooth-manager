package org.sputnikdev.bluetooth.manager.impl;

import java.util.List;

public interface Service<T> extends BluetoothObject<T> {

    String getUUID();

    List<Characteristic> getCharacteristics();

}
