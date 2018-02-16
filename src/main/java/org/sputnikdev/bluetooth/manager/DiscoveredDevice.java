package org.sputnikdev.bluetooth.manager;

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

import java.util.Objects;


/**
 * Objects of this class capture discovery results for Bluetooth devices.
 *
 * @author Vlad Kolotov
 */
public class DiscoveredDevice implements DiscoveredObject {

    private static final String COMBINED_DEVICE_ADDRESS = CombinedGovernor.COMBINED_ADDRESS;
    
    private final URL url;
    private final String name;
    private final String alias;
    private short rssi;
    private int bluetoothClass;
    private boolean bleEnabled;

    /**
     * Creates a new instance based on previously created object.
     * @param device device to copy
     */
    public DiscoveredDevice(DiscoveredDevice device) {
        this(device.url, device.name, device.alias, device.rssi, device.bluetoothClass, device.bleEnabled);
    }

    /**
     * Creates a new object.
     * @param url bluetooth object URL
     * @param name bluetooth object name
     * @param alias bluetooth object alias
     * @param rssi bluetooth object RSSI
     * @param bluetoothClass bluetooth object class
     * @param bleEnabled indicated if it is a BLE enabled device
     */
    public DiscoveredDevice(URL url, String name, String alias, short rssi, int bluetoothClass, boolean bleEnabled) {
        this.url = url;
        this.name = name;
        this.alias = alias;
        this.rssi = rssi;
        this.bluetoothClass = bluetoothClass;
        this.bleEnabled = bleEnabled;
    }

    /**
     * Returns bluetooth object URL.
     * @return bluetooth object URL
     */
    @Override
    public URL getURL() {
        return url;
    }

    /**
     * Returns bluetooth object name.
     * @return bluetooth object name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns bluetooth object alias.
     * @return bluetooth object alias
     */
    @Override
    public String getAlias() {
        return alias;
    }

    /**
     * Returns bluetooth object RSSI.
     * @return bluetooth object RSSI
     */
    public short getRSSI() {
        return rssi;
    }

    /**
     * Returns bluetooth object class.
     * @return bluetooth object class
     */
    public int getBluetoothClass() {
        return bluetoothClass;
    }

    /**
     * Indicates if this device is Bluetooth Low Energy enabled device.
     * @return true if Bluetooth Low Energy enabled device, false otherwise
     */
    public boolean isBleEnabled() {
        return bleEnabled;
    }

    @Override
    public boolean isCombined() {
        return COMBINED_DEVICE_ADDRESS.equalsIgnoreCase(url.getAdapterAddress());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiscoveredDevice)) {
            return false;
        }
        DiscoveredDevice that = (DiscoveredDevice) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        String displayName = alias != null ? alias : name;
        return "[Device] " + getURL() + " [" + displayName + "]";
    }
}
