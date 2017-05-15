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


/**
 * Objects of this class capture discovery results for Bluetooth adapters and devices.
 * To check the type of the bluetooth object use its URL:
 * <pre>
 *     <code>
 * DiscoveredDevice device = ...;
 * device.getURL().isAdapter();
 * device.getURL().isDevice();
 *     </code>
 * </pre>
 *
 * @author Vlad Kolotov
 */
public class DiscoveredDevice {
    
    private final URL url;
    private final String name;
    private final String alias;
    private short rssi;
    private int bluetoothClass;

    /**
     * Creates a new object.
     * @param url bluetooth object URL
     * @param name bluetooth object name
     * @param alias bluetooth object alis
     */
    public DiscoveredDevice(URL url, String name, String alias) {
        this.url = url;
        this.name = name;
        this.alias = alias;
    }

    /**
     * Creates a new object.
     * @param url bluetooth object URL
     * @param name bluetooth object name
     * @param alias bluetooth object alis
     * @param rssi bluetooth object RSSI
     * @param bluetoothClass bluetooth object class
     */
    public DiscoveredDevice(URL url, String name, String alias, short rssi, int bluetoothClass) {
        this.url = url;
        this.name = name;
        this.alias = alias;
        this.rssi = rssi;
        this.bluetoothClass = bluetoothClass;
    }

    /**
     * Returns bluetooth object URL
     * @return bluetooth object URL
     */
    public URL getURL() {
        return url;
    }

    /**
     * Returns bluetooth object name
     * @return bluetooth object name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns bluetooth object alias
     * @return bluetooth object alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns bluetooth object RSSI
     * @return bluetooth object RSSI
     */
    public short getRSSI() {
        return rssi;
    }

    /**
     * Returns bluetooth object class
     * @return bluetooth object class
     */
    public int getBluetoothClass() {
        return bluetoothClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DiscoveredDevice that = (DiscoveredDevice) o;
        return url.equals(that.url);

    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    @Override
    public String toString() {
        String displayName = alias != null ? alias : name;
        return (url.getDeviceAddress() != null ? "[Device] " : "[Adapter] ") + getURL() + " [" + displayName + "]";
    }
}
