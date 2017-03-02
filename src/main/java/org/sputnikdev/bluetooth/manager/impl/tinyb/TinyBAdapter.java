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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.impl.Adapter;
import org.sputnikdev.bluetooth.manager.impl.Device;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothNotification;

/**
 *
 * @author Vlad Kolotov
 */
public class TinyBAdapter implements Adapter<BluetoothAdapter> {

    private final BluetoothAdapter adapter;

    public TinyBAdapter(BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public URL getURL() {
        return new URL(getAddress());
    }

    @Override
    public String getAlias() {
        return adapter.getAlias();
    }

    @Override
    public String getName() {
        return adapter.getName();
    }

    @Override
    public void setAlias(String s) {
        adapter.setAlias(s);
    }

    @Override
    public String getAddress() {
        return adapter.getAddress();
    }

    @Override
    public boolean isPowered() {
        return adapter.getPowered();
    }

    @Override
    public void enablePoweredNotifications(Notification<Boolean> notification) {
        adapter.enablePoweredNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean powered) {
                notification.notify(powered);
            }
        });
    }

    @Override
    public void disablePoweredNotifications() {
        adapter.disablePoweredNotifications();
    }

    @Override
    public void setPowered(boolean b) {
        adapter.setPowered(b);
    }

    @Override
    public boolean isDiscovering() {
        return adapter.getDiscovering();
    }

    @Override
    public void enableDiscoveringNotifications(Notification<Boolean> notification) {
        adapter.enableDiscoveringNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableDiscoveringNotifications() {
        adapter.disableDiscoveringNotifications();
    }

    @Override
    public boolean startDiscovery() throws BluetoothException {
        return adapter.startDiscovery();
    }

    @Override
    public boolean stopDiscovery() throws BluetoothException {
        return adapter.stopDiscovery();
    }

    @Override
    public List<Device<?>> getDevices() {
        List<BluetoothDevice> devices = adapter.getDevices();
        List<Device<?>> result = new ArrayList<>(devices.size());
        for (BluetoothDevice device : devices) {
            if (device.getRSSI() != 0) {
                result.add(new TinyBDevice(device));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
