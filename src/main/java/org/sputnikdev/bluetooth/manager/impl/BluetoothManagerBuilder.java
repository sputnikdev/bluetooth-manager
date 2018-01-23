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
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.lang.reflect.Constructor;

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
    private boolean tinybTransport;
    private String bluegigaRegex;
    private boolean started = true;
    private boolean discovering;

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
     * If set to true, all discovered devices with the same address (but discovered through different adapters)
     * are combined into a single device and therefore can be controlled as a single unit.
     * @param combinedDevices if true, all discovered devices with the same address are combined into a single device
     */
    public BluetoothManagerBuilder withCombinedDevices(boolean combinedDevices) {
        this.combinedDevices = combinedDevices;
        return this;
    }

    /**
     * Is set to true, TinyB transport is registered (if found in the classpath).
     * @param tinybTransport register TinyB transport
     */
    public BluetoothManagerBuilder withTinyBTransport(boolean tinybTransport) {
        this.tinybTransport = tinybTransport;
        return this;
    }

    /**
     * If the provided argument is not null, then BlueGiga is registered with the provided regular expression
     * for BlueGiga adapters (serial ports) auto discovery.
     * WARNING!: It is very important not to make the regular expression too wide so that ONLY Bluegiga
     * adapters/ serial ports are matched by the regular expression. If the regular expression is too broad,
     * this can lead to hardware malfunction/damage of other devices that are accidentally matched by the
     * regular expression. USE THIS FEATURE AT YOUR OWN RISK.
     * <br>Regular expression examples:
     * <ul>
     *  <li>Defining some specific serial ports (preferable option): (/dev/ttyACM1)|(/dev/ttyACM2)</li>
     *  <li>Matching all serial ports on Linux: ((/dev/ttyACM)[0-9]{1,3})</li>
     *  <li>Matching all serial ports on OSX: (/dev/tty.(usbmodem).*)</li>
     *  <li>Matching all serial ports on Windows: ((COM)[0-9]{1,3})</li>
     *  <li>Matching all serial ports with some exclusions: (?!/dev/ttyACM0|/dev/ttyACM5)((/dev/ttyACM)[0-9]{1,3})</li>
     *  <li>Default regular expression is to match nothing: (?!)</li>
     * </ul>
     * @param tinybTransport register BlueGiga transport
     */
    public BluetoothManagerBuilder withBlueGigaTransport(String bluegigaRegex) {
        this.bluegigaRegex = bluegigaRegex;
        return this;
    }

    /**
     * If set to true, bluetooth manager will be started.
     * @param started if true, bluetooth manager will be started
     */
    public BluetoothManagerBuilder withStarted(boolean started) {
        this.started = started;
        return this;
    }

    /**
     * If set to true, the discovery process will be enabled.
     * @param started if true, the discovery process will be enabled
     */
    public BluetoothManagerBuilder withDiscovering(boolean discovering) {
        this.discovering = discovering;
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
        if (tinybTransport) {
            loadTinyBTransport(manager);
        }
        if (bluegigaRegex != null) {
            loadBlueGigaTransport(manager);
        }
        if (started) {
            manager.start(discovering);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(manager::dispose));

        return manager;
    }

    private void loadTinyBTransport(BluetoothManager bluetoothManager) {
        try {
            Class<?> tinybFactoryClass =
                    Class.forName("org.sputnikdev.bluetooth.manager.transport.tinyb.TinyBFactory");
            tinybFactoryClass.getDeclaredMethod("loadNativeLibraries").invoke(tinybFactoryClass);
            bluetoothManager.registerFactory((BluetoothObjectFactory) tinybFactoryClass.newInstance());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void loadBlueGigaTransport(BluetoothManager bluetoothManager) {
        try {
            Class<?> bluegigaFactoryClass =
                    Class.forName("org.sputnikdev.bluetooth.manager.transport.bluegiga.BluegigaFactory");
            Constructor<?> constructor = bluegigaFactoryClass.getConstructor(String.class);
            bluetoothManager.registerFactory((BluetoothObjectFactory) constructor.newInstance(bluegigaRegex));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
