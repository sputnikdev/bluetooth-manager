package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sputnikdev.bluetooth.manager.impl.Characteristic;
import org.sputnikdev.bluetooth.manager.impl.Service;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;


public class TinyBService implements Service<BluetoothGattService> {

    private final BluetoothGattService service;

    @Override public String getUUID() {
        return service.getUUID();
    }

    public TinyBService(BluetoothGattService service) {
        this.service = service;
    }

    @Override public List<Characteristic> getCharacteristics() {
        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        List<Characteristic> result = new ArrayList<>(characteristics.size());
        for (BluetoothGattCharacteristic nativeCharacteristic : characteristics) {
            result.add(new TinyBCharacteristic(nativeCharacteristic));
        }
        return Collections.unmodifiableList(result);
    }
}
