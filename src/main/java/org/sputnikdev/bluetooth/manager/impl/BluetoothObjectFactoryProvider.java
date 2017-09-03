package org.sputnikdev.bluetooth.manager.impl;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Device;

/**
 * A root interface for all Bluetooth transport implementations.
 *
 * @author Vlad Kolotov
 */
public class BluetoothObjectFactoryProvider {

    private static final Map<String, BluetoothObjectFactory> factories = new HashMap<>();

    /**
     * Registers a new Bluetooth Object factory
     * @param transport a new Bluetooth Object factory
     */
    public static void registerFactory(BluetoothObjectFactory transport) {
        synchronized (factories) {
            factories.put(transport.getProtocolName(), transport);
        }
    }

    /**
     * Un-registers a previously registered Bluetooth Object factory
     * @param transport a Bluetooth Object factory
     */
    public static void unregisterFactory(BluetoothObjectFactory transport) {
        synchronized (factories) {
            ((BluetoothManagerImpl) BluetoothManagerFactory.getManager()).resetDescendants(
                    new URL().copyWithProtocol(transport.getProtocolName()));
            factories.remove(transport.getProtocolName());
        }
    }

    /**
     * Returns a Bluetooth Object factory by its protocol name.
     * @param protocolName protocol name
     * @return a Bluetooth Object factory
     */
    protected static BluetoothObjectFactory getFactory(String protocolName) {
        synchronized (factories) {
            if (!factories.containsKey(protocolName)) {
                throw new IllegalStateException("Transport [" + protocolName + "] is not registered.");
            }
            return factories.get(protocolName);
        }
    }

    /**
     * Returns all discovered devices by all registered transports.
     * @return all discovered devices
     */
    protected static List<DiscoveredDevice> getAllDiscoveredDevices() {
        synchronized (factories) {
            List<DiscoveredDevice> devices = new ArrayList<>();
            for (BluetoothObjectFactory bluetoothObjectFactory : factories.values()) {
                List<DiscoveredDevice> factoryDevices = bluetoothObjectFactory.getDiscoveredDevices();
                if (factoryDevices != null) {
                    devices.addAll(bluetoothObjectFactory.getDiscoveredDevices());
                }
            }
            return devices;
        }
    }

    /**
     * Returns all discovered adapters by all registered transports.
     * @return all discovered adapters
     */
    protected static List<DiscoveredAdapter> getAllDiscoveredAdapters() {
        synchronized (factories) {
            List<DiscoveredAdapter> adapters = new ArrayList<>();
            for (BluetoothObjectFactory bluetoothObjectFactory : factories.values()) {
                adapters.addAll(bluetoothObjectFactory.getDiscoveredAdapters());
            }
            return adapters;
        }
    }

}
