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

import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;

/**
 * Bluetooth Manager instance builder.
 *
 * @author Vlad Kolotov
 */
public class BluetoothManagerBuilder {

    private int discoveryRate = BluetoothManagerImpl.DISCOVERY_RATE_SEC;
    private boolean rediscover;
    private int refreshRate = BluetoothManagerImpl.REFRESH_RATE_SEC;
    private boolean combinedAdapters;
    private boolean combinedDevices = true;

    /**
     * Sets how frequent the discovery process should update its state.
     * Note: discovery rate must be set before calling {@link BluetoothManager#start} method
     * @param seconds discovery rate in seconds
     */
    public BluetoothManagerBuilder withDiscoveryRate(int seconds) {
        discoveryRate = seconds;
        return this;
    }

    /**
     * Sets whether the discovery process should repeatedly notify clients
     * ({@link AdapterDiscoveryListener#discovered(DiscoveredAdapter)} and
     * {@link DeviceDiscoveryListener#discovered(DiscoveredDevice)}) about discovered devices every update step.
     * See {@link #setDiscoveryRate(int)} to set discovery rate
     * @param rediscover controls whether clients
     */
    public BluetoothManagerBuilder withRediscover(boolean rediscover) {
        this.rediscover = rediscover;
        return this;
    }

    /**
     * Sets the refresh rate which controls how often bluetooth devices are checked/updated.
     * Restart is required if the manager is already started.
     * @param refreshRate refresh rate
     */
    public BluetoothManagerBuilder withRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
        return this;
    }

    /**
     * If set to true all discovered adapters are combined into a single adapter and therefore can be controlled as
     * a single unit.
     * @param combinedAdapters if true, all discovered adapters are combined into a single adapter
     */
    public BluetoothManagerBuilder withCombinedAdapters(boolean combinedAdapters) {
        this.combinedAdapters = combinedAdapters;
        return this;
    }

    /**
     * If set to true all discovered devices with the same address (but discovered through different adapters)
     * are combined into a single device and therefore can be controlled as a single unit.
     * @param combinedDevices if true, all discovered devices with the same address are combined into a single device
     */
    public BluetoothManagerBuilder withCombinedDevices(boolean combinedDevices) {
        this.combinedDevices = combinedDevices;
        return this;
    }

    /**
     * Builds a new instance of the Bluetooth Manager.
     * @return a new instance of the Bluetooth Manager
     */
    public BluetoothManager build() {
        BluetoothManagerImpl manager = new BluetoothManagerImpl();
        manager.setDiscoveryRate(discoveryRate);
        manager.setRediscover(rediscover);
        manager.setRefreshRate(refreshRate);
        manager.enableCombinedAdapters(combinedAdapters);
        manager.enableCombinedDevices(combinedDevices);
        return manager;
    }

}
