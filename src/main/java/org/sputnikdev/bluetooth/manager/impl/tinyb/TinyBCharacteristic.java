package org.sputnikdev.bluetooth.manager.impl.tinyb;

import org.sputnikdev.bluetooth.manager.impl.Characteristic;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothNotification;

public class TinyBCharacteristic implements Characteristic<BluetoothGattCharacteristic> {

    private final BluetoothGattCharacteristic characteristic;

    public TinyBCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }

    @Override
    public String getUUID() {
        return characteristic.getUUID();
    }

    @Override
    public String[] getFlags() {
        return characteristic.getFlags();
    }

    @Override
    public boolean isNotifying() {
        return characteristic.getNotifying();
    }

    @Override
    public byte[] readValue() throws BluetoothException {
        return characteristic.readValue();
    }

    @Override
    public void
    enableValueNotifications(Notification<byte[]> notification) {
        characteristic.enableValueNotifications(new BluetoothNotification<byte[]>() {
            @Override public void run(byte[] bytes) {
                notification.notify(bytes);
            }
        });
    }

    @Override
    public void disableValueNotifications() {
        characteristic.disableValueNotifications();
    }

    @Override
    public boolean writeValue(byte[] bytes) throws BluetoothException {
        return characteristic.writeValue(bytes);
    }
}
