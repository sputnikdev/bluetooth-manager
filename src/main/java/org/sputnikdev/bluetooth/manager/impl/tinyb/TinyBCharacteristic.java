package org.sputnikdev.bluetooth.manager.impl.tinyb;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.impl.Characteristic;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothNotification;

/**
 *
 * @author Vlad Kolotov
 */
public class TinyBCharacteristic implements Characteristic<BluetoothGattCharacteristic> {

    private final BluetoothGattCharacteristic characteristic;

    public TinyBCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }

    @Override
    public URL getURL() {
        return new URL(characteristic.getService().getDevice().getAdapter().getAddress(),
                characteristic.getService().getDevice().getAddress(), characteristic.getService().getUUID(),
                characteristic.getUUID());
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
