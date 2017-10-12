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

import com.google.common.collect.Sets;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * Thread safe bluetooth manager implementation class.
 * @author Vlad Kolotov
 */
class BluetoothManagerImpl implements BluetoothManager {

    private static final int REFRESH_RATE_SEC = 5;
    private static final int DISCOVERY_RATE_SEC = 10;

    private Logger logger = LoggerFactory.getLogger(BluetoothManagerImpl.class);

    private final ScheduledExecutorService singleThreadScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Set<DeviceDiscoveryListener> deviceDiscoveryListeners = new CopyOnWriteArraySet<>();
    private final Set<AdapterDiscoveryListener> adapterDiscoveryListeners = new CopyOnWriteArraySet<>();

    private final Map<URL, BluetoothObjectGovernor> governors = new ConcurrentHashMap<>();

    private ScheduledFuture discoveryFuture;
    private final Map<URL, ScheduledFuture> governorFutures = new ConcurrentHashMap<>();

    private final Set<DiscoveredDevice> discoveredDevices = new CopyOnWriteArraySet<>();
    private final Set<DiscoveredAdapter> discoveredAdapters = new CopyOnWriteArraySet<>();

    private final Map<String, String> adapterToProtocolCache = new HashMap<>();

    private boolean startDiscovering;
    private int discoveryRate = DISCOVERY_RATE_SEC;
    private int refreshRate = REFRESH_RATE_SEC;
    private boolean rediscover;

    @Override
    public void start(boolean startDiscovering) {
        synchronized (singleThreadScheduler) {
            if (discoveryFuture == null) {
                this.startDiscovering = startDiscovering;
                discoveryFuture = singleThreadScheduler.scheduleAtFixedRate(
                    new DiscoveryJob(), 0, discoveryRate, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (singleThreadScheduler) {
            if (discoveryFuture != null) {
                discoveryFuture.cancel(true);
                discoveryFuture = null;
            }
        }
    }

    @Override
    public void addDeviceDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.add(deviceDiscoveryListener);
    }

    @Override
    public void removeDeviceDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.remove(deviceDiscoveryListener);
    }

    @Override
    public void addAdapterDiscoveryListener(AdapterDiscoveryListener adapterDiscoveryListener) {
        adapterDiscoveryListeners.add(adapterDiscoveryListener);
    }

    @Override
    public void removeAdapterDiscoveryListener(AdapterDiscoveryListener adapterDiscoveryListener) {
        adapterDiscoveryListeners.remove(adapterDiscoveryListener);
    }

    @Override
    public void disposeGovernor(URL url) {
        if (governors.containsKey(url)) {
            disposeGovernor(governors.get(url));
        }
    }

    @Override
    public void disposeDescendantGovernors(URL url) {
        governors.values().stream().filter(g -> g.getURL().isDescendant(url)).forEach(this::disposeGovernor);
    }

    @Override
    public AdapterGovernor getAdapterGovernor(URL url) {
        return (AdapterGovernorImpl) getGovernor(url.getAdapterURL());
    }

    @Override
    public DeviceGovernor getDeviceGovernor(URL url) {
        return (DeviceGovernorImpl) getGovernor(url.getDeviceURL());
    }

    @Override
    public DeviceGovernor getDeviceGovernorAutoconnect(URL url) {
        DeviceGovernor deviceGovernor = getDeviceGovernor(url);
        deviceGovernor.setConnectionControl(true);
        return deviceGovernor;
    }

    @Override
    public CharacteristicGovernor getCharacteristicGovernor(URL url) {
        return (CharacteristicGovernorImpl) getGovernor(url.getCharacteristicURL());
    }

    @Override
    public CharacteristicGovernor getCharacteristicGovernorAutoconnect(URL url) {
        getDeviceGovernor(url).setConnectionControl(true);
        return getCharacteristicGovernor(url);
    }

    @Override
    public void dispose() {
        logger.info("Disposing Bluetooth manager");

        singleThreadScheduler.shutdown();
        scheduler.shutdown();
        if (discoveryFuture != null) {
            discoveryFuture.cancel(true);
        }
        governorFutures.values().forEach(future -> future.cancel(true));

        deviceDiscoveryListeners.clear();
        adapterDiscoveryListeners.clear();

        governors.values().forEach(this::reset);
        governors.clear();

        logger.info("Bluetooth service has been disposed");
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() {
        return Collections.unmodifiableSet(discoveredDevices);
    }

    @Override
    public Set<DiscoveredAdapter> getDiscoveredAdapters() {
        return Collections.unmodifiableSet(discoveredAdapters);
    }

    @Override
    public BluetoothGovernor getGovernor(URL url) {
        synchronized (governors) {
            if (!governors.containsKey(url)) {
                BluetoothObjectGovernor governor = createGovernor(url);

                governors.put(url, governor);
                governorFutures.put(url,
                        scheduler.scheduleWithFixedDelay(() -> update(governor),5, refreshRate, TimeUnit.SECONDS));

                update(governor);

                return governor;
            }
            return governors.get(url);
        }
    }

    @Override
    public void setDiscoveryRate(int seconds) {
        this.discoveryRate = seconds;
    }

    @Override
    public void setRediscover(boolean rediscover) {
        this.rediscover = rediscover;
    }

    @Override
    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    List<BluetoothGovernor> getGovernors(List<? extends BluetoothObject> objects) {
        return Collections.unmodifiableList(objects.stream()
            .map(o -> getGovernor(o.getURL())).collect(Collectors.toList()));
    }

    void updateDescendants(URL parent) {
        governors.values().stream().filter(g -> g.getURL().isDescendant(parent)).forEach(this::update);
    }

    void resetDescendants(URL parent) {
        governors.values().stream().filter(g -> g.getURL().isDescendant(parent)).forEach(this::reset);
    }

    <T extends BluetoothObject> T getBluetoothObject(URL url) {
        BluetoothObjectFactory factory = findFactory(url);
        BluetoothObject bluetoothObject = null;
        if (factory != null) {
            URL objectURL = url.copyWithProtocol(factory.getProtocolName());
            if (url.isAdapter()) {
                bluetoothObject = factory.getAdapter(objectURL);
            } else if (url.isDevice()) {
                bluetoothObject = factory.getDevice(objectURL);
            } else if (url.isCharacteristic()) {
                bluetoothObject = factory.getCharacteristic(objectURL);
            }
        }
        return (T) bluetoothObject;
    }

    BluetoothObjectGovernor createGovernor(URL url) {
        if (url.isAdapter()) {
            AdapterGovernorImpl adapterGovernor = new AdapterGovernorImpl(this, url);
            adapterGovernor.setDiscoveringControl(startDiscovering);
            return adapterGovernor;
        } else if (url.isDevice()) {
            return new DeviceGovernorImpl(this, url);
        } else if (url.isCharacteristic()) {
            return new CharacteristicGovernorImpl(this, url);
        }
        throw new IllegalStateException("Unknown url");
    }

    private void disposeGovernor(BluetoothObjectGovernor governor) {
        URL url = governor.getURL();
        reset(governor);
        synchronized (governorFutures) {
            if (governorFutures.containsKey(url)) {
                governorFutures.get(url).cancel(true);
                governorFutures.remove(url);
            }
        }
        governors.remove(url);
    }

    private BluetoothObjectFactory findFactory(URL url) {
        String protocol = url.getProtocol();
        String adapterAddress = url.getAdapterAddress();
        if (url.getProtocol() != null) {
            return BluetoothObjectFactoryProvider.getFactory(protocol);
        } else if (adapterToProtocolCache.containsKey(adapterAddress)) {
            return BluetoothObjectFactoryProvider.getFactory(adapterToProtocolCache.get(adapterAddress));
        } else {
            for (DiscoveredAdapter adapter : BluetoothObjectFactoryProvider.getAllDiscoveredAdapters()) {
                if (adapter.getURL().getAdapterAddress().equals(adapterAddress)) {
                    adapterToProtocolCache.put(url.getAdapterAddress(), adapter.getURL().getProtocol());
                    return BluetoothObjectFactoryProvider.getFactory(adapter.getURL().getProtocol());
                }
            }
        }
        return null;
    }

    private void notifyDeviceDiscovered(DiscoveredDevice device) {
        if (discoveredDevices.contains(device) && !rediscover) {
            return;
        }
        deviceDiscoveryListeners.forEach(deviceDiscoveryListener -> {
            try {
                deviceDiscoveryListener.discovered(device);
            } catch (Exception ex) {
                logger.error("Discovery listener error (device)", ex);
            }
        });
    }

    private void notifyAdapterDiscovered(DiscoveredAdapter adapter) {
        if (discoveredAdapters.contains(adapter) && !rediscover) {
            return;
        }
        adapterDiscoveryListeners.forEach(adapterDiscoveryListener -> {
            try {
                adapterDiscoveryListener.discovered(adapter);
            } catch (Exception ex) {
                logger.error("Discovery listener error (adapter)", ex);
            }
        });
    }

    private void handleDeviceLost(URL url) {
        logger.info("Device has been lost: " + url);
        deviceDiscoveryListeners.forEach(deviceDiscoveryListener -> {
            try {
                deviceDiscoveryListener.deviceLost(url);
            } catch (Throwable ex) {
                logger.error("Device listener error", ex);
            }
        });
    }

    private void handleAdapterLost(URL url) {
        logger.info("Adapter has been lost: " + url);
        adapterDiscoveryListeners.forEach(adapterDiscoveryListener -> {
            try {
                adapterDiscoveryListener.adapterLost(url);
            } catch (Throwable ex) {
                logger.error("Adapter listener error", ex);
            }
        });
        reset((BluetoothObjectGovernor) getAdapterGovernor(url));
    }

    private void reset(BluetoothObjectGovernor governor) {
        try {
            governor.reset();
        } catch (Exception ex) {
            logger.error("Could not reset governor: " + governor, ex);
        }
    }

    private void update(BluetoothObjectGovernor governor) {
        try {
            governor.update();
        } catch (Exception ex) {
            logger.warn("Could not update governor: " + governor, ex);
        }
    }

    private class DiscoveryJob implements Runnable {

        @Override
        public void run() {
            try {
                discoverAdapters();
            } catch (Exception ex) {
                logger.error("Adapter discovery job error", ex);
            }
            try {
                discoverDevices();
            } catch (Exception ex) {
                logger.error("Device discovery job error", ex);
            }
        }

        private void discoverDevices() {
            synchronized (discoveredDevices) {
                List<DiscoveredDevice> list = BluetoothObjectFactoryProvider.getAllDiscoveredDevices();
                if (list == null) {
                    return;
                }

                Set<DiscoveredDevice> newDiscovery = new HashSet<>();
                for (DiscoveredDevice discoveredDevice : list) {
                    short rssi = discoveredDevice.getRSSI();
                    if (rssi == 0) {
                        continue;
                    }
                    notifyDeviceDiscovered(discoveredDevice);
                    newDiscovery.add(discoveredDevice);
                }
                for (DiscoveredDevice lost : Sets.difference(discoveredDevices, newDiscovery)) {
                    handleDeviceLost(lost.getURL());
                }
                discoveredDevices.clear();
                discoveredDevices.addAll(newDiscovery);
            }
        }

        private void discoverAdapters() {
            synchronized (discoveredAdapters) {
                Set<DiscoveredAdapter> newDiscovery = new HashSet<>();
                for (DiscoveredAdapter discoveredAdapter :
                        BluetoothObjectFactoryProvider.getAllDiscoveredAdapters()) {
                    notifyAdapterDiscovered(discoveredAdapter);
                    newDiscovery.add(discoveredAdapter);
                    if (startDiscovering) {
                        // create (if not created before) adapter governor which will trigger its discovering status
                        // (by default when it is created "discovering" flag is set to true)
                        getAdapterGovernor(discoveredAdapter.getURL());
                    }
                }
                for (DiscoveredAdapter lost : Sets.difference(discoveredAdapters, newDiscovery)) {
                    handleAdapterLost(lost.getURL());
                }
                discoveredAdapters.clear();
                discoveredAdapters.addAll(newDiscovery);
            }
        }
    }

}
