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
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.ManagerListener;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread safe bluetooth manager implementation class.
 * @author Vlad Kolotov
 */
class BluetoothManagerImpl implements BluetoothManager {

    static final int REFRESH_RATE_SEC = 5;
    static final int DISCOVERY_RATE_SEC = 10;
    static final long DISCOVERY_STALE_DEVICE_REMOVAL_TIMEOUT = 1000 * 60 * 10;

    private Logger logger = LoggerFactory.getLogger(BluetoothManagerImpl.class);

    private final Map<String, BluetoothObjectFactory> factories = new ConcurrentHashMap<>();

    private final ExecutorService notificationService = Executors.newCachedThreadPool();

    private final ScheduledExecutorService discoveryScheduler = Executors.newScheduledThreadPool(6);
    private final ScheduledExecutorService governorScheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, ScheduledFuture<?>> adapterDiscoveryFutures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> deviceDiscoveryFutures = new ConcurrentHashMap<>();
    private final Map<URL, ScheduledFuture<?>> governorFutures = new HashMap<>();

    private final Set<DeviceDiscoveryListener> deviceDiscoveryListeners = new CopyOnWriteArraySet<>();
    private final Set<AdapterDiscoveryListener> adapterDiscoveryListeners = new CopyOnWriteArraySet<>();
    private final Set<ManagerListener> managerListeners = new CopyOnWriteArraySet<>();

    private final Map<URL, BluetoothObjectGovernor> governors = new ConcurrentHashMap<>();
    private final Map<URL, DeviceDiscoveryHolder> discoveredDevices = new ConcurrentHashMap<>();
    private final Set<DiscoveredAdapter> discoveredAdapters = new CopyOnWriteArraySet<>();

    private boolean startDiscovering;
    private int discoveryRate = DISCOVERY_RATE_SEC;
    private int refreshRate = REFRESH_RATE_SEC;
    private boolean rediscover;
    private boolean started;
    private boolean combinedAdapters;
    private boolean combinedDevices = true;

    @Override
    public void start(boolean startDiscovering) {
        logger.debug("Starting bluetooth manager: {}", Integer.toHexString(hashCode()));
        if (started || !adapterDiscoveryFutures.isEmpty() || !deviceDiscoveryFutures.isEmpty()
            || !governorFutures.isEmpty()) {
            return;
        }
        this.startDiscovering = startDiscovering;
        synchronized (factories) {
            factories.values().forEach(this::scheduleDiscovery);
        }
        synchronized (governorScheduler) {
            governors.values().forEach(this::scheduleGovernor);
        }
        started = true;
    }

    @Override
    public void registerFactory(BluetoothObjectFactory transport) {
        logger.debug("Register {} transport", transport.getProtocolName());
        synchronized (factories) {
            factories.computeIfAbsent(transport.getProtocolName(), protocolName -> {
                if (started) {
                    scheduleDiscovery(transport);
                }
                return transport;
            });
        }
    }

    @Override
    public void unregisterFactory(BluetoothObjectFactory transport) {
        logger.debug("Unregister {} transport", transport.getProtocolName());
        synchronized (factories) {
            factories.computeIfPresent(transport.getProtocolName(), (protocolName, factory) -> {
                handleObjectFactoryUnregistered(factory);
                return null;
            });
        }
    }

    @Override
    public void stop() {
        logger.debug("Stopping bluetooth manager: {}", Integer.toHexString(hashCode()));
        cancelAllFutures(false);
        started = false;
    }

    @Override
    public boolean isStarted() {
        return started;
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
    public BluetoothGovernor getGovernor(URL url) {
        logger.trace("Governor requested: {}", url);
        if (url.isProtocol() || url.isRoot()) {
            return null;
        }
        URL protocolLess = url.copyWithProtocol(null);
        BluetoothObjectGovernor governor = governors.get(protocolLess);
        if (governor == null) {
            logger.trace("Governor does not exist: {}", protocolLess);
            synchronized (governors) {
                // is it still missing?
                if (!governors.containsKey(protocolLess)) {
                    governor = createGovernor(protocolLess);
                    governors.put(protocolLess, governor);
                    init(governor);
                    scheduleGovernor(governor);
                    return governor;
                } else {
                    governor = governors.get(protocolLess);
                    logger.trace("Returning an existing governor: {}", governor.getURL());
                }
            }
        }
        if (!governor.isReady()) {
            logger.debug("Governor is not ready. Enforcing an explicit update: {}", governor.getURL());
            update(governor);
        }
        return governor;
    }

    @Override
    public AdapterGovernor getAdapterGovernor(URL url) {
        return (AdapterGovernor) getGovernor(url.getAdapterURL());
    }

    @Override
    public DeviceGovernor getDeviceGovernor(URL url) {
        return (DeviceGovernor) getGovernor(url.getDeviceURL());
    }

    @Override
    public DeviceGovernor getDeviceGovernor(URL url, boolean forceConnect) {
        DeviceGovernor deviceGovernor = getDeviceGovernor(url);
        if (forceConnect) {
            logger.debug("Forcing device governor to be connected: {}", deviceGovernor.getURL());
            deviceGovernor.setConnectionControl(true);
            if (!deviceGovernor.isReady() || !deviceGovernor.isConnected()) {
                logger.debug("Governor is not connected. Enforcing an explicit update: {}", deviceGovernor.getURL());
                update((BluetoothObjectGovernor) deviceGovernor);
            }
        }
        return deviceGovernor;
    }

    @Override
    public CharacteristicGovernor getCharacteristicGovernor(URL url) {
        return (CharacteristicGovernor) getGovernor(url.getCharacteristicURL());
    }

    @Override
    public CharacteristicGovernor getCharacteristicGovernor(URL url, boolean forceConnect) {
        CharacteristicGovernor characteristicGovernor = getCharacteristicGovernor(url);
        if (forceConnect) {
            DeviceGovernor deviceGovernor = getDeviceGovernor(url, true);
            if (deviceGovernor.isReady() && deviceGovernor.isConnected() && !characteristicGovernor.isReady()) {
                logger.debug("Device connected. Enforcing an explicit update for characteristic: {}", deviceGovernor);
                update((BluetoothObjectGovernor) characteristicGovernor);
            }
        }
        return characteristicGovernor;
    }

    @Override
    public void dispose() {
        logger.debug("Disposing Bluetooth manager: {}", Integer.toHexString(hashCode()));

        notificationService.shutdownNow();

        cancelAllFutures(true);

        governorScheduler.shutdown();
        discoveryScheduler.shutdown();

        deviceDiscoveryListeners.clear();
        adapterDiscoveryListeners.clear();

        factories.clear();

        Map<URL, BluetoothObjectGovernor> tmp = new HashMap<>(governors);
        governors.clear();
        tmp.values().forEach(this::dispose);

        logger.debug("Bluetooth manager has been disposed: {}", Integer.toHexString(hashCode()));
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() {
        long current = System.currentTimeMillis();
        Stream<Map.Entry<URL, DeviceDiscoveryHolder>> filtered = discoveredDevices.entrySet().stream()
                .filter(entry -> isStale(entry.getValue(), current));
        if (combinedDevices) {
            Map<URL, List<DeviceDiscoveryHolder>> groupedByDeviceAddress =
                    filtered.collect(Collectors.groupingBy(entry ->
                                            entry.getKey().copyWithAdapter(CombinedGovernor.COMBINED_ADDRESS),
                                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
            return Collections.unmodifiableSet(new HashSet<>(groupedByDeviceAddress.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> entry.getValue().stream().reduce(DeviceDiscoveryHolder::merge).get())).values()));
        } else {
            return Collections.unmodifiableSet(filtered.map(Map.Entry::getValue).collect(Collectors.toSet()));
        }
    }

    @Override
    public Set<DiscoveredAdapter> getDiscoveredAdapters() {
        if (combinedAdapters) {
            return discoveredAdapters.stream().map(adapter -> {
                return new DiscoveredAdapter(new URL("/" + CombinedGovernor.COMBINED_ADDRESS),
                        adapter.getName(), adapter.getAlias());
            }).collect(Collectors.toSet());
        } else {
            return Collections.unmodifiableSet(discoveredAdapters);
        }
    }

    @Override
    public boolean isCombinedAdaptersEnabled() {
        return combinedAdapters;
    }

    @Override
    public boolean isCombinedDevicesEnabled() {
        return combinedDevices;
    }

    @Override
    public void addManagerListener(ManagerListener listener) {
        managerListeners.add(listener);
    }

    @Override
    public void removeManagerListener(ManagerListener listener) {
        managerListeners.remove(listener);
    }

    protected void disposeDescendantGovernors(URL url) {
        logger.debug("Explicitly disposing descendant governors: {}", url);
        computeForEachDescendantGovernorAndRemove(url, this::disposeGovernor);
    }

    protected void disposeGovernor(URL url) {
        logger.debug("Explicitly disposing governor: {}", url);
        synchronized (governors) {
            URL protocolLess = url.copyWithProtocol(null);
            if (governors.containsKey(protocolLess)) {
                BluetoothObjectGovernor governor = governors.get(protocolLess);
                disposeGovernor(governor);
                governors.remove(protocolLess);
            }
        }
    }

    protected void scheduleUpdate(BluetoothObjectGovernor governor) {
        if (!governorScheduler.isShutdown()) {
            governorScheduler.submit(() -> update(governor));
        }
    }

    protected void notify(Runnable runnable) {
        if (!notificationService.isShutdown()) {
            notificationService.submit(runnable);
        }
    }

    protected <V> void notify(Consumer<V> consumer, V value) {
        if (!notificationService.isShutdown()) {
            notificationService.submit(() -> {
                consumer.accept(value);
            });
        }
    }

    protected <T, V> void notify(List<T> listeners, BiConsumer<T, V> consumer, V value, Logger lgr, String errorLogMessage) {
        if (!notificationService.isShutdown()) {
            notificationService.submit(() -> {
                BluetoothManagerUtils.forEachSilently(listeners, consumer, value, lgr, errorLogMessage);
            });
        }
    }

    BluetoothObjectFactory getFactory(String protocolName) {
        logger.trace("Getting registered transport (factory): {}", protocolName);
        BluetoothObjectFactory factory = factories.get(protocolName);
        if (factory == null) {
            logger.warn("Transport [{}] is not registered.", protocolName);
        }
        return factory;
    }

    void setDiscoveryRate(int discoveryRate) {
        this.discoveryRate = discoveryRate;
    }

    void setRediscover(boolean rediscover) {
        this.rediscover = rediscover;
    }

    void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    void enableCombinedAdapters(boolean combineAdapters) {
        combinedAdapters = combineAdapters;
    }

    void enableCombinedDevices(boolean combineDevices) {
        combinedDevices = combineDevices;
    }

    protected void notifyGovernorReady(BluetoothGovernor governor, boolean ready) {
        logger.debug("Notifying manager listeners (governor ready): {} : {}",
                managerListeners.size(), ready);
        BluetoothManagerUtils.forEachSilently(managerListeners, listener -> listener.ready(governor, ready), logger,
                "Error in manager listener: ready");
    }

    List<BluetoothGovernor> getGovernors(List<? extends BluetoothObject> objects) {
        return Collections.unmodifiableList(objects.stream()
            .map(o -> getGovernor(o.getURL())).collect(Collectors.toList()));
    }

    void updateDescendants(URL parent) {
        computeForEachDescendantGovernor(parent, this::update);
    }

    void resetDescendants(URL parent) {
        if (parent.isProtocol()) {
            // reset all governors that belongs to the transport specified in the argument
            governors.values().stream().filter(governor -> governor instanceof AbstractBluetoothObjectGovernor)
                .map(governor -> (AbstractBluetoothObjectGovernor) governor)
                .filter(governor -> parent.getProtocol().equals(governor.getTransport()))
                .forEach(this::reset);
        } else {
            computeForEachDescendantGovernor(parent, this::reset);
        }
    }

    /**
     * This is a very centric method that returns a "native" objects. Mostly used by governors to acquire
     * a corresponding native object.
     * @param url bluetooth url
     * @return a native object corresponding to the given url
     */
    <T extends BluetoothObject> T getBluetoothObject(URL url) {
        logger.trace("Native object requested: {}", url);
        BluetoothObjectFactory factory = findFactory(url);
        logger.trace("Factory found: {} : {}", url, factory != null ? factory.getProtocolName() : null);
        BluetoothObject bluetoothObject = null;
        if (factory != null) {
            URL objectURL = url.copyWithProtocol(factory.getProtocolName());
            if (objectURL.isAdapter()) {
                bluetoothObject = factory.getAdapter(objectURL);
            } else if (objectURL.isDevice()) {
                bluetoothObject = factory.getDevice(objectURL);
            } else if (objectURL.isCharacteristic()) {
                bluetoothObject = factory.getCharacteristic(objectURL);
            }
        }
        logger.trace("Returning native object: {} : {}", url, bluetoothObject);
        return (T) bluetoothObject;
    }

    void disposeBluetoothObject(URL url) {
        logger.trace("Disposing native object: {}", url);
        Optional.ofNullable(findFactory(url)).ifPresent(factory -> factory.dispose(url));
    }

    BluetoothObjectGovernor createGovernor(URL url) {
        logger.debug("Creating a new governor: {}", url);
        if (CombinedGovernor.COMBINED_ADDRESS.equals(url.getAdapterAddress())) {
            return createCombinedGovernor(url);
        } else {
            return createBasicGovernor(url);
        }
    }

    private BluetoothObjectGovernor createCombinedGovernor(URL url) {
        if (url.isAdapter()) {
            AdapterGovernor adapterGovernor = new CombinedAdapterGovernorImpl(this, url);
            adapterGovernor.setDiscoveringControl(startDiscovering);
            return (BluetoothObjectGovernor) adapterGovernor;
        } else if (url.isDevice()) {
            return new CombinedDeviceGovernorImpl(this, url);
        } else if (url.isCharacteristic()) {
            return new CombinedCharacteristicGovernorImpl(this, url);
        }
        throw new IllegalStateException("Unknown url");
    }

    private BluetoothObjectGovernor createBasicGovernor(URL url) {
        if (url.isAdapter()) {
            AdapterGovernor adapterGovernor = new AdapterGovernorImpl(this, url);
            adapterGovernor.setDiscoveringControl(startDiscovering);
            return (BluetoothObjectGovernor) adapterGovernor;
        } else if (url.isDevice()) {
            return new DeviceGovernorImpl(this, url);
        } else if (url.isCharacteristic()) {
            return new CharacteristicGovernorImpl(this, url);
        }
        throw new IllegalStateException("Unknown url");
    }

    private void handleObjectFactoryUnregistered(BluetoothObjectFactory bluetoothObjectFactory) {
        String protocol = bluetoothObjectFactory.getProtocolName();
        synchronized (discoveryScheduler) {
            cancelFutures(adapterDiscoveryFutures, protocol);
            cancelFutures(deviceDiscoveryFutures, protocol);
        }
        resetDescendants(new URL().copyWithProtocol(protocol));
    }

    Set<URL> getRegisteredGovernors() {
        return Collections.unmodifiableSet(governors.keySet());
    }

    boolean isGovernorRegistered(URL url) {
        return governors.containsKey(url)
                || governors.containsKey(url.copyWithProtocol(null))
                || governors.containsKey(url.copyWithProtocol(null).copyWithAdapter(CombinedGovernor.COMBINED_ADDRESS));
    }

    private void disposeGovernor(BluetoothObjectGovernor governor) {
        governorFutures.computeIfPresent(governor.getURL(), (url, future) -> {
            future.cancel(true);
            return null;
        });
        dispose(governor);
    }

    private BluetoothObjectFactory findFactory(URL url) {
        logger.debug("Trying to find object factory: {}", url);
        String protocol = url.getProtocol();
        String adapterAddress = url.getAdapterAddress();
        if (url.getProtocol() != null) {
            return getFactory(protocol);
        } else {
            logger.debug("Protocol is unknown. Trying to find factory amongst discovered adapters: {}", url);
            for (DiscoveredAdapter adapter : discoveredAdapters) {
                if (adapter.getURL().getAdapterAddress().equals(adapterAddress)) {
                    logger.debug("Matching adapter found amongst discovered adapters: {}", url);
                    return getFactory(adapter.getURL().getProtocol());
                }
            }
        }
        return null;
    }

    private void notifyDeviceDiscovered(DiscoveredDevice device) {
        logger.debug("Notifying device discovery listeners (discovered): {} : {}",
                device, deviceDiscoveryListeners.size());
        BluetoothManagerUtils.forEachSilently(deviceDiscoveryListeners,
            listener -> {
                if (!combinedDevices || listener instanceof CombinedDeviceGovernorImpl) {
                    listener.discovered(device);
                } else {
                    listener.discovered(new DiscoveredDevice(
                            device.getURL().copyWithAdapter(CombinedGovernor.COMBINED_ADDRESS),
                            device.getName(), device.getAlias(), device.getRSSI(), device.getBluetoothClass(),
                            device.isBleEnabled()));
                }
            }, logger, "Error in device discovery listener");
    }

    private void notifyAdapterDiscovered(DiscoveredAdapter adapter) {
        if (discoveredAdapters.contains(adapter) && !rediscover) {
            return;
        }
        BluetoothManagerUtils.forEachSilently(adapterDiscoveryListeners,
            listener -> {
                if (!combinedAdapters || listener instanceof CombinedAdapterGovernorImpl) {
                    listener.discovered(adapter);
                } else {
                    listener.discovered(new DiscoveredAdapter(new URL("/" + CombinedGovernor.COMBINED_ADDRESS),
                            "Combined Bluetooth Adapter", null));
                }
            }, logger, "Error in adapter discovery listener");
    }

    private void notifyDeviceLost(URL url) {
        logger.debug("Device has been lost: " + url);
        BluetoothManagerUtils.forEachSilently(deviceDiscoveryListeners, DeviceDiscoveryListener::deviceLost, url,
                logger, "Error in device discovery listener");
    }

    private void handleAdapterLost(URL url) {
        logger.debug("Adapter has been lost: " + url);
        BluetoothManagerUtils.forEachSilently(adapterDiscoveryListeners, AdapterDiscoveryListener::adapterLost, url,
                logger, "Error in adapter discovery listener");
        reset((BluetoothObjectGovernor) getAdapterGovernor(url));
    }

    private void reset(BluetoothObjectGovernor governor) {
        try {
            logger.debug("Resetting governor: {}", governor.getURL());
            governor.reset();
        } catch (Exception ex) {
            logger.warn("Error occurred while resetting governor: " + governor, ex);
        }
    }

    private void dispose(BluetoothObjectGovernor governor) {
        try {
            logger.debug("Disposing governor: {}", governor.getURL());
            governor.dispose();
        } catch (Exception ex) {
            logger.warn("Error occurred while disposing governor: " + governor, ex);
        }
    }

    private void update(BluetoothObjectGovernor governor) {
        try {
            logger.trace("Updating governor: {}", governor.getURL());
            governor.update();
        } catch (Exception ex) {
            logger.warn("Error occurred while updating governor: " + governor, ex);
        }
    }

    private void init(BluetoothObjectGovernor governor) {
        try {
            logger.debug("Initializing governor: {}", governor.getURL());
            governor.init();
        } catch (Exception ex) {
            logger.warn("Error occurred while initializing governor: " + governor, ex);
        }
    }

    private static final class DeviceDiscoveryHolder extends DiscoveredDevice {
        private long timestamp;

        private DeviceDiscoveryHolder(DiscoveredDevice device, long timestamp) {
            super(device);
            this.timestamp = timestamp;
        }

        private DeviceDiscoveryHolder(URL url, String name, String alias, short rssi,
                                 int bluetoothClass, boolean bleEnabled, long timestamp) {
            super(url, name, alias, rssi, bluetoothClass, bleEnabled);
            this.timestamp = timestamp;
        }

        private DeviceDiscoveryHolder merge(DiscoveredDevice device, long timestamp) {
            String name = getName();
            if (!BluetoothManagerUtils.isMacAddress(device.getName())) {
                name = device.getName();
            }
            return new DeviceDiscoveryHolder(getURL(), name,
                    device.getAlias() != null ? device.getAlias() : getAlias(),
                    device.getRSSI(), device.getBluetoothClass() > 0 ? device.getBluetoothClass() : getBluetoothClass(),
                    device.isBleEnabled() ? true : isBleEnabled(), Math.max(this.timestamp, timestamp));
        }

        private DeviceDiscoveryHolder merge(DeviceDiscoveryHolder device) {
            return merge(device, device.timestamp);
        }

    }

    private final class DeviceDiscoveryJob implements Runnable {

        private final BluetoothObjectFactory factory;

        private DeviceDiscoveryJob(BluetoothObjectFactory factory) {
            this.factory = factory;
        }

        @Override
        public void run() {
            try {
                discoverDevices();
            } catch (Exception ex) {
                logger.warn("Device discovery job error", ex);
            }
        }

        private void discoverDevices() {
            Set<DiscoveredDevice> discovered = factory.getDiscoveredDevices();
            logger.debug("Transport [{}] reported {} discovered devices", factory.getProtocolName(), discovered.size());

            Set<DiscoveredDevice> factoryDevices = discoveredDevices.values().stream()
                    .filter(device -> factory.getProtocolName().equals(device.getURL().getProtocol()))
                    .collect(Collectors.toSet());

            Set<DiscoveredDevice> lost = Sets.difference(factoryDevices, discovered);
            Set<DiscoveredDevice> newDevices = Sets.difference(discovered, factoryDevices);
            Set<DiscoveredDevice> rediscovered = Sets.intersection(discovered, factoryDevices);

            // notify listeners about lost devices and remove from discovered devises list
            handleLost(lost);

            // notify listeners about new devices and add them to the discovered devices list
            handleNew(newDevices);

            // check if new results are better than we have already (e.g. aliases resolved or bluetooth class etc)
            // also try to detect stale results (devices that stopped advertising) preventing building them up
            // also re-notify if "rediscover" is enabled
            handleExisting(rediscovered);

            long current = System.currentTimeMillis();
            Set<DeviceDiscoveryHolder> stale = discoveredDevices.values().stream()
                    .filter(device -> isStale(device, current)).collect(Collectors.toSet());
            logger.debug("Lost: {}; New: {}; Rediscovered: {}; Stale: {}",
                    lost.size(), newDevices.size(), rediscovered.size(), stale.size());
        }

        private void handleLost(Set<DiscoveredDevice> lost) {
            lost.forEach(device -> {
                notifyDeviceLost(device.getURL());
                discoveredDevices.remove(device.getURL());
            });
        }

        private void handleNew(Set<DiscoveredDevice> newDevices) {
            long current = System.currentTimeMillis();
            newDevices.forEach(device -> {
                notifyDeviceDiscovered(device);
                discoveredDevices.put(device.getURL(), new DeviceDiscoveryHolder(device, current));
            });
        }

        private void handleExisting(Set<DiscoveredDevice> rediscovered) {
            long current = System.currentTimeMillis();
            rediscovered.forEach(rediscoveredDevice -> {
                discoveredDevices.compute(rediscoveredDevice.getURL(), (url, existingDevice) -> {
                    // check if factory reports a device with the same RSSI for a long time
                    // check if governor registered, we don't want to remove a device that is required by a governor
                    // if both conditions are true, then remove stale device
                    DeviceDiscoveryHolder device;
                    if (!isGovernorRegistered(url) && existingDevice.getRSSI() == rediscoveredDevice.getRSSI()) {
                        if (isStale(existingDevice, current)) {
                            logger.warn("Removing stale device: {} : {} ",
                                    existingDevice.getURL(), existingDevice.getRSSI());
                            factory.dispose(existingDevice.getURL());
                            notifyDeviceLost(existingDevice.getURL());
                        }
                        device = existingDevice;
                    } else {
                        device = existingDevice.merge(rediscoveredDevice, current);
                    }
                    if (rediscover && current - existingDevice.timestamp < DISCOVERY_STALE_DEVICE_REMOVAL_TIMEOUT) {
                        notifyDeviceDiscovered(device);
                    }
                    return device;
                });
            });
        }
    }

    private static boolean isStale(DeviceDiscoveryHolder device, long current) {
        return current - device.timestamp > DISCOVERY_STALE_DEVICE_REMOVAL_TIMEOUT;
    }

    private final class AdapterDiscoveryJob implements Runnable {

        private final BluetoothObjectFactory factory;

        private AdapterDiscoveryJob(BluetoothObjectFactory factory) {
            this.factory = factory;
        }

        @Override
        public void run() {
            try {
                discoverAdapters();
            } catch (Exception ex) {
                logger.warn("Adapter discovery job error", ex);
            }
        }

        private void discoverAdapters() {
            Set<DiscoveredAdapter> discovered = factory.getDiscoveredAdapters();
            logger.debug("Transport [{}] reported {} discovered adapters",
                    factory.getProtocolName(), discovered.size());

            discovered.forEach(adapter -> {
                notifyAdapterDiscovered(adapter);
                if (startDiscovering) {
                    // create (if not created before) adapter governor which will trigger its discovering status
                    // (by default when it is created "discovering" flag is set to true)
                    getAdapterGovernor(adapter.getURL());
                }
            });

            Set<DiscoveredAdapter> factoryAdapters = discoveredAdapters.stream()
                    .filter(device -> factory.getProtocolName().equals(device.getURL().getProtocol()))
                    .collect(Collectors.toSet());

            Set<DiscoveredAdapter> lostAdapters = Sets.difference(factoryAdapters, discovered);
            lostAdapters.forEach(lost -> handleAdapterLost(lost.getURL()));

            discoveredAdapters.removeAll(lostAdapters);
            discoveredAdapters.addAll(discovered);
        }
    }

    private void computeForEachDescendantGovernorAndRemove(URL url, Consumer<BluetoothObjectGovernor> consumer) {
        URL protocolLess = url.copyWithProtocol(null);
        governors.entrySet().removeIf(entry -> {
            if (entry.getKey().isDescendant(protocolLess)) {
                consumer.accept(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void computeForEachDescendantGovernor(URL url, Consumer<BluetoothObjectGovernor> consumer) {
        URL protocolLess = url.copyWithProtocol(null);
        governors.values().stream().filter(governor -> governor.getURL().isDescendant(protocolLess)).forEach(consumer);
    }

    private void scheduleDiscovery(BluetoothObjectFactory factory) {
        AdapterDiscoveryJob adapterDiscoveryJob = new AdapterDiscoveryJob(factory);
        adapterDiscoveryJob.run();
        adapterDiscoveryFutures.put(factory.getProtocolName(),
            discoveryScheduler.scheduleWithFixedDelay(adapterDiscoveryJob, 5, discoveryRate, TimeUnit.SECONDS));

        DeviceDiscoveryJob deviceDiscoveryJob = new DeviceDiscoveryJob(factory);
        deviceDiscoveryJob.run();
        deviceDiscoveryFutures.put(factory.getProtocolName(),
            discoveryScheduler.scheduleWithFixedDelay(deviceDiscoveryJob, 5, discoveryRate, TimeUnit.SECONDS));
    }

    private void scheduleGovernor(BluetoothObjectGovernor governor) {
        governorFutures.put(governor.getURL(),
            governorScheduler.scheduleWithFixedDelay(() -> update(governor),5, refreshRate, TimeUnit.SECONDS));
    }

    private void cancelAllFutures(boolean forceInterrupt) {
        synchronized (discoveryScheduler) {
            adapterDiscoveryFutures.values().forEach(future -> future.cancel(forceInterrupt));
            adapterDiscoveryFutures.clear();
            deviceDiscoveryFutures.values().forEach(future -> future.cancel(forceInterrupt));
            deviceDiscoveryFutures.clear();
        }
        synchronized (governorScheduler) {
            governorFutures.values().forEach(future -> future.cancel(forceInterrupt));
            governorFutures.clear();
        }
    }

    private static void cancelFutures(Map<String, ScheduledFuture<?>> futures, String transport) {
        futures.entrySet().removeIf(entry -> {
            if (entry.getKey().equals(transport)) {
                entry.getValue().cancel(true);
                return true;
            }
            return false;
        });
    }

}
