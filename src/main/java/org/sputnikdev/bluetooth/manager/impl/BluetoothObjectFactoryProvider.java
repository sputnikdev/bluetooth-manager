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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A root interface for all Bluetooth transport implementations.
 *
 * @author Vlad Kolotov
 */
public class BluetoothObjectFactoryProvider {

    private static final Map<String, BluetoothObjectFactory> factories = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(BluetoothObjectFactoryProvider.class);

    /**
     * Registers a new Bluetooth Object factory.
     * @param transport a new Bluetooth Object factory
     */
    public static void registerFactory(BluetoothObjectFactory transport) {
        factories.put(transport.getProtocolName(), transport);
        getBluetoothManager().handleObjectFactoryRegistered(transport);
    }

    /**
     * Un-registers a previously registered Bluetooth Object factory.
     * @param transport a Bluetooth Object factory
     */
    public static void unregisterFactory(BluetoothObjectFactory transport) {
        synchronized (factories) {
            getBluetoothManager().handleObjectFactoryUnregistered(transport);
            factories.remove(transport.getProtocolName());
        }
    }

    /**
     * Returns a Bluetooth Object factory by its protocol name.
     * @param protocolName protocol name
     * @return a Bluetooth Object factory
     */
    public static BluetoothObjectFactory getFactory(String protocolName) {
        BluetoothObjectFactory factory = factories.get(protocolName);
        if (factory == null) {
            LOGGER.debug("Transport [" + protocolName + "] is not registered.");
        }
        return factory;
    }

    /**
     * Returns registered Bluetooth Object factories.
     * @return a Bluetooth Object factories
     */
    public static List<BluetoothObjectFactory> getRegisteredFactories() {
        return new ArrayList<>(factories.values());
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

    private static BluetoothManagerImpl getBluetoothManager() {
        return (BluetoothManagerImpl) BluetoothManagerFactory.getManager();
    }

}
