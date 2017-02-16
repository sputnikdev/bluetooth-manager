package org.sputnikdev.bluetooth.manager;


public class GattCharacteristic {

    private final String uuid;
    private final String[] flags;

    public GattCharacteristic(String uuid, String[] flags) {
        this.uuid = uuid;
        this.flags = flags;
    }

    public String getUUID() {
        return uuid;
    }

    public String[] getFlags() {
        return flags;
    }
}
