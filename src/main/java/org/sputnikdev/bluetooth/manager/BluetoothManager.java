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
import org.sputnikdev.bluetooth.manager.impl.BluetoothManagerBuilder;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.util.Set;

/**
 * The core of the system. Provides various high level methods for accessing bluetooth object governors
 * ({@link BluetoothGovernor}) as well as subscribing for bluetooth object discovery events.
 *
 * <p>Start using it by accessing a default implementation:
 * <pre>
 * {@code
 * BluetoothManager manager = BluetoothManagerFactory.getManager();
 * manager.addDiscoveryListener(...);
 * manager.start(true);
 * }
 * </pre>
 *
 * @author Vlad Kolotov
 */
public interface BluetoothManager {

    /**
     * Starts bluetooth manager background activities.
     *
     * <p>If true is provided for the argument, then bluetooth manager will activate bluetooth device discovery
     * process for all available bluetooth adapters. Discovered bluetooth adapters and devices will be available:
     * <ul>
     *     <li>By executing {@link BluetoothManager#getDiscoveredDevices()}</li>
     *     <li>By listening to discovery events via {@link DeviceDiscoveryListener}</li>
     * </ul>
     *
     * <p>If false is provided for the argument, then bluetooth manager won't activate device discovery process.
     * However, it is possible to activate device discovery process for a particular bluetooth adapter
     * if its MAC address is known:
     * <pre>
     * {@code
     *
     * BluetoothManager manager = BluetoothManagerFactory.getManager();
     * manager.addDiscoveryListener(...);
     * manager.start(false);
     * manager.getAdapterGovernor(new URL("/XX:XX:XX:XX:XX:XX")).setDiscoveringControl(true);
     * }
     * </pre>
     *
     * @param startDiscovering controls whether bluetooth manager should start bluetooth device discovery
     */
    void start(boolean startDiscovering);

    /**
     * Shuts down all bluetooth manager background activities.
     */
    void stop();

    /**
     * Checks whether the bluetooth manager has been started.
     * @return true if started, false otherwise
     */
    boolean isStarted();

    /**
     * Register a new device discovery listener.
     *
     * @param deviceDiscoveryListener a new device discovery listener
     */
    void addDeviceDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);

    /**
     * Unregisters a device discovery listener.
     * @param deviceDiscoveryListener a device discovery listener
     */
    void removeDeviceDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);

    /**
     * Register a new adapter discovery listener.
     *
     * @param adapterDiscoveryListener a new device discovery listener
     */
    void addAdapterDiscoveryListener(AdapterDiscoveryListener adapterDiscoveryListener);

    /**
     * Unregisters a adapter discovery listener.
     * @param adapterDiscoveryListener a device discovery listener
     */
    void removeAdapterDiscoveryListener(AdapterDiscoveryListener adapterDiscoveryListener);

    /**
     * Return a list of discovered bluetooth devices.
     * @return a list of discovered bluetooth devices
     */
    Set<DiscoveredDevice> getDiscoveredDevices();

    /**
     * Return a list of discovered bluetooth adapters.
     * @return a list of discovered bluetooth adapters
     */
    Set<DiscoveredAdapter> getDiscoveredAdapters();

    /**
     * Creates a new bluetooth governor or returns an existing one by its URL.
     *
     * @param url a URL of a bluetooth object (adapter, device, characteristic)
     * @return a bluetooth governor
     */
    BluetoothGovernor getGovernor(URL url);

    /**
     * Creates a new adapter governor or returns an existing one by its URL.
     * @param url a URL of a bluetooth adapter
     * @return an adapter governor
     */
    AdapterGovernor getAdapterGovernor(URL url);

    /**
     * Creates a new device governor or returns an existing one by its URL.
     * @param url a URL of a bluetooth device
     * @return an device governor
     */
    DeviceGovernor getDeviceGovernor(URL url);

    /**
     * Creates a new device governor or returns an existing one by its URL.
     * If the provided boolean argument is set to true, then the connection control
     * ({@link DeviceGovernor#setConnectionControl(boolean)}) is enabled for the governor.
     * @param url a URL of a bluetooth device
     * @param forceConnect if set to true, governor is forced to establish connection to the device
     * @return an device governor
     */
    DeviceGovernor getDeviceGovernor(URL url, boolean forceConnect);

    /**
     * Creates a new characteristic governor or returns an existing one by its URL.
     * @param url a URL of a bluetooth characteristic
     * @return a characteristic governor
     */
    CharacteristicGovernor getCharacteristicGovernor(URL url);

    /**
     * Creates a new characteristic governor or returns an existing one by its URL.
     * If the provided boolean argument is set to true, then the connection control
     * ({@link DeviceGovernor#setConnectionControl(boolean)}) is enabled for the corresponding governor.
     * @param url a URL of a bluetooth characteristic
     * @param forceConnect if set to true, the corresponding DeviceGovernor connection control is set to true
     * @return a characteristic governor
     */
    CharacteristicGovernor getCharacteristicGovernor(URL url, boolean forceConnect);

    /**
     * Disposes/ shuts down the bluetooth manager and its governors.
     */
    void dispose();

    /**
     * Checks whether the bluetooth manager is in the "combine adapters" mode
     * ({@link BluetoothManagerBuilder#withCombinedAdapters(boolean)}.
     * @return true if the "combined adapters" mode is enabled
     */
    boolean isCombinedAdaptersEnabled();

    /**
     * Checks whether the bluetooth manager is in the "combine devices" mode
     * ({@link BluetoothManagerBuilder#withCombinedDevices(boolean)}}).
     * @return true if the "combined devices" mode is enabled
     */
    boolean isCombinedDevicesEnabled();

    /**
     * Adds a new bluetooth manager listener.
     * @param listener a manager listener
     */
    void addManagerListener(ManagerListener listener);

    /**
     * Removes an existing bluetooth manager listener.
     * @param listener an existing bluetooth manager listener
     */
    void removeManagerListener(ManagerListener listener);

    /**
     * Registers a new Bluetooth Object factory (transport).
     * @param transport a new Bluetooth Object factory
     */
    void registerFactory(BluetoothObjectFactory transport);

    /**
     * Un-registers a previously registered Bluetooth Object factory (transport).
     * @param transport a Bluetooth Object factory
     */
    void unregisterFactory(BluetoothObjectFactory transport);

    /**
     * Returns the refresh rate of how often bluetooth devices are checked/updated.
     * @return refresh rate
     */
    int getRefreshRate();

}
