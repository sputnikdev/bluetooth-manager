package org.sputnikdev.bluetooth.manager;

import java.util.Collections;
import java.util.List;

public class GattService {

    private final String uuid;
    private final List<GattCharacteristic> characteristics;

    public GattService(String uuid, List<GattCharacteristic> characteristics) {
        this.uuid = uuid;
        this.characteristics = Collections.unmodifiableList(characteristics);
    }

    public String getUUID() {
        return uuid;
    }

    public List<GattCharacteristic> getCharacteristics() {
        return characteristics;
    }

}
