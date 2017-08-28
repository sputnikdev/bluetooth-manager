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

import java.util.Set;

import org.sputnikdev.bluetooth.URL;

/**
 * The core of the system. Provides various high level methods for accessing bluetooth object governors
 * ({@link BluetoothGovernor}) as well as subscribing for bluetooth object discovery events.
 *
 * Start using it by accessing a default implementation:
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
     * If true is provided for the argument, then bluetooth manager will activate bluetooth device discovery
     * process for all available bluetooth adapters. Discovered bluetooth adapters and devices will be available:
     * <ul>
     *     <li>By executing {@link BluetoothManager#getDiscoveredDevices()}</li>
     *     <li>By listening to discovery events via {@link DeviceDiscoveryListener}</li>
     * </ul>
     *
     * If false is provided for the argument, then bluetooth manager won't activate device discovery process.
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
     *
     * @param startDiscovering controls whether bluetooth manager should start bluetooth device discovery
     */
    void start(boolean startDiscovering);

    /**
     * Shuts down all bluetooth manager background activities.
     */
    void stop();

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
     * Creates a new characteristic governor or returns an existing one by its URL.
     * @param url a URL of a bluetooth characteristic
     * @return a characteristic governor
     */
    CharacteristicGovernor getCharacteristicGovernor(URL url);

    /**
     * Disposes/ shuts down a governor by its URL.
     * @param url a URL of a bluetooth object (adapter, device, characteristic)
     */
    void disposeGovernor(URL url);

    /**
     * Disposes/ shuts down descendant governors by provided URL ({@link URL#isDescendant(URL)}).
     * @param url a URL of a bluetooth object (adapter, device, characteristic)
     */
    void disposeDescendantGovernors(URL url);

    /**
     * Disposes/ shuts down the bluetooth manager and its governors.
     */
    void dispose();

    /**
     * Sets how frequent the discovery process should update its state.
     * Note: discovery rate must be set before calling {@link BluetoothManager#start} method
     * @param seconds discovery rate in seconds
     */
    void setDiscoveryRate(int seconds);

    /**
     * Sets whether the discovery process should repeatedly notify clients
     * ({@link AdapterDiscoveryListener#discovered(DiscoveredAdapter)} and
     * {@link DeviceDiscoveryListener#discovered(DiscoveredDevice)}) about discovered devices every update step.
     * See {@link #setDiscoveryRate(int)} to set discovery rate
     * @param rediscover controls whether clients
     */
    void setRediscover(boolean rediscover);

    /**
     * Sets the refresh rate which controls how often bluetooth devices are checked/updated.
     * Restart is required if the manager is already started.
     * @param refreshRate refresh rate
     */
    void setRefreshRate(int refreshRate);

}
