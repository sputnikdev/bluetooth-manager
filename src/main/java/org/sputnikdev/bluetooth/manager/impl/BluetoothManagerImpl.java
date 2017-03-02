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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;

/**
 *
 * @author Vlad Kolotov
 */
class BluetoothManagerImpl implements BluetoothManager {

    private Logger logger = LoggerFactory.getLogger(BluetoothManagerImpl.class);

    private final ScheduledExecutorService singleThreadScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Set<DeviceDiscoveryListener> deviceDiscoveryListeners = new HashSet<>();

    private final Map<URL, BluetoothObjectGovernor> governors = new HashMap<>();

    private ScheduledFuture discoveryFuture;
    private final Map<URL, ScheduledFuture> governorFutures = new HashMap<>();

    private final Set<DiscoveredDevice> discovered = new HashSet<>();

    @Override
    public synchronized void startDiscovery() {
        try {
            if (discoveryFuture == null) {
                discoveryFuture = singleThreadScheduler.scheduleAtFixedRate(
                        new DiscoveryJob(), 0, 10, TimeUnit.SECONDS);
            }
        } catch (BluetoothException ex) {
            logger.error("Could not start discovery", ex);
        }
    }

    @Override
    public synchronized void stopDiscovery() {
        try {
            if (discoveryFuture != null) {
                tinyb.BluetoothManager.getBluetoothManager().stopDiscovery();
                discoveryFuture.cancel(true);
            }
        } catch (BluetoothException ex) {
            logger.error("Could not stop discovery", ex);
        }
    }

    @Override
    public void addDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.add(deviceDiscoveryListener);
    }

    @Override
    public void removeDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.remove(deviceDiscoveryListener);
    }

    @Override
    public void disposeGovernor(URL url) {
        synchronized (governors) {
            if (governors.containsKey(url)) {
                governors.get(url).dispose();
                synchronized (governorFutures) {
                    if (governorFutures.containsKey(url)) {
                        governorFutures.get(url).cancel(true);
                        governorFutures.remove(url);
                    }
                }
                governors.remove(url);
            }
        }
    }

    @Override
    public DeviceGovernorImpl getDeviceGovernor(URL url) {
        return (DeviceGovernorImpl) getGovernor(url.getDeviceURL());
    }
    @Override
    public AdapterGovernorImpl getAdapterGovernor(URL url) {
        return (AdapterGovernorImpl) getGovernor(url.getAdapterURL());
    }
    @Override
    public CharacteristicGovernorImpl getCharacteristicGovernor(URL url) {
        return (CharacteristicGovernorImpl) getGovernor(url.getCharacteristicURL());
    }

    @Override
    public void dispose() {
        logger.info("Disposing Bluetooth service");

        singleThreadScheduler.shutdown();
        scheduler.shutdown();
        if (discoveryFuture != null) {
            discoveryFuture.cancel(true);
        }
        for (ScheduledFuture future : Sets.newHashSet(governorFutures.values())) {
            future.cancel(true);
        }
        deviceDiscoveryListeners.clear();

        synchronized (governors) {
            for (BluetoothObjectGovernor governor : governors.values()) {
                try {
                    governor.dispose();
                } catch (Exception ex) {
                    logger.error("Could not dispose governor: " + governor.getURL());
                }
            }
            governors.clear();
        }
        logger.info("Bluetooth service has been disposed");
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() {
        synchronized (discovered) {
            return Collections.unmodifiableSet(discovered);
        }
    }

    @Override
    public BluetoothObjectGovernor getGovernor(URL url) {
        synchronized (governors) {
            if (!governors.containsKey(url)) {
                BluetoothObjectGovernor governor = createGovernor(url);

                update(governor);

                governors.put(url, governor);
                governorFutures.put(url,
                        scheduler.scheduleAtFixedRate((Runnable) () -> update(governor), 5, 5, TimeUnit.SECONDS));

                return governor;
            }
            return governors.get(url);
        }
    }

    List<BluetoothObjectGovernor> getGovernors(List<? extends BluetoothObject> objects) {
        List<BluetoothObjectGovernor> result = new ArrayList<>(objects.size());
        synchronized (governors) {
            for (BluetoothObject object : objects) {
                result.add(getGovernor(object.getURL()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    void updateDescendants(URL parent) {
        synchronized (governors) {
            for (BluetoothObjectGovernor governor : governors.values()) {
                if (governor.getURL().isDescendant(parent)) {
                    update(governor);
                }
            }
        }
    }

    void resetDescendants(URL parent) {
        synchronized (governors) {
            for (BluetoothObjectGovernor governor : governors.values()) {
                if (governor.getURL().isDescendant(parent)) {
                    governor.reset();
                }
            }
        }
    }


    private BluetoothObjectGovernor createGovernor(URL url) {
        if (url.isAdapter()) {
            return new AdapterGovernorImpl(this, url);
        } else if (url.isDevice()) {
            return new DeviceGovernorImpl(this, url);
        } else if (url.isCharacteristic()) {
            return new CharacteristicGovernorImpl(this, url);
        }
        throw new IllegalStateException("Unknown url");
    }

    private void notifyDeviceDiscovered(DiscoveredDevice device) {
        if (this.discovered.contains(device)) {
            return;
        }
        for (DeviceDiscoveryListener deviceDiscoveryListener : Lists.newArrayList(deviceDiscoveryListeners)) {
            try {
                deviceDiscoveryListener.discovered(device);
            } catch (Exception ex) {
                logger.error("Discovery listener error", ex);
            }
        }
    }

    private DiscoveredDevice getDiscoveredDevice(BluetoothDevice device) {
        return new DiscoveredDevice(new URL(device.getAdapter().getAddress(), device.getAddress()),
                device.getName(), device.getAlias(), device.getRSSI(),
                device.getBluetoothClass());
    }

    private DiscoveredDevice getDiscoveredAdapter(BluetoothAdapter adapter) {
        return new DiscoveredDevice(new URL(adapter.getAddress(), null), adapter.getName(), adapter.getAlias());
    }

    private void notifyDeviceLost(URL url) {
        logger.info("Device has been lost: " + url.getAdapterAddress() + " - " + url.getDeviceAddress());
        for (DeviceDiscoveryListener deviceDiscoveryListener : Lists.newArrayList(deviceDiscoveryListeners)) {
            try {
                deviceDiscoveryListener.lost(url);
            } catch (Throwable ex) {
                logger.error("Device listener error", ex);
            }
        }
        try {
            getDeviceGovernor(url).reset();
        } catch (Throwable ex) {
            logger.warn("Could not reset device governor", ex);
        }
    }

    private void update(BluetoothObjectGovernor governor) {
        try {
            governor.update();
        } catch (Throwable ex) {
            logger.error("Could not update governor: " + governor.getURL(), ex);
        }
    }

    private class DiscoveryJob implements Runnable {

        @Override
        public void run() {
            try {
                synchronized (discovered) {
                    List<BluetoothDevice> list = tinyb.BluetoothManager.getBluetoothManager().getDevices();
                    if (list == null) {
                        return;
                    }

                    Set<DiscoveredDevice> newDiscovery = new HashSet<>();
                    for (BluetoothDevice device : list) {
                        short rssi = device.getRSSI();
                        if (rssi == 0) {
                            continue;
                        }
                        DiscoveredDevice discoveredDevice = getDiscoveredDevice(device);
                        notifyDeviceDiscovered(discoveredDevice);
                        newDiscovery.add(discoveredDevice);

                    }
                    for (BluetoothAdapter adapter : tinyb.BluetoothManager.getBluetoothManager().getAdapters()) {
                        DiscoveredDevice discoveredAdapter = getDiscoveredAdapter(adapter);
                        notifyDeviceDiscovered(discoveredAdapter);
                        newDiscovery.add(discoveredAdapter);
                    }
                    for (DiscoveredDevice lost : Sets.difference(discovered, newDiscovery)) {
                        notifyDeviceLost(lost.getURL());
                    }
                    discovered.clear();
                    discovered.addAll(newDiscovery);
                }
            } catch (Exception ex) {
                logger.error("Discovery job error", ex);
            }
        }
    }

}
