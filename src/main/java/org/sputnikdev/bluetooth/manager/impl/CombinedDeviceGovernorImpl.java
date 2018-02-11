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
import org.sputnikdev.bluetooth.Filter;
import org.sputnikdev.bluetooth.RssiKalmanFilter;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.CombinedDeviceGovernor;
import org.sputnikdev.bluetooth.manager.ConnectionStrategy;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GattCharacteristic;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author Vlad Kolotov
 */
class CombinedDeviceGovernorImpl implements DeviceGovernor, CombinedDeviceGovernor, BluetoothObjectGovernor {

    // when RSSI reading is deemed to be stale for the nearest adapter calculation
    private static final int STALE_TIMEOUT = 10000;

    private Logger logger = LoggerFactory.getLogger(CombinedDeviceGovernorImpl.class);

    private final BluetoothManagerImpl bluetoothManager;
    private final URL url;

    private final AtomicInteger governorsCount = new AtomicInteger();
    private final Map<URL, DeviceGovernorHandler> governors = new ConcurrentHashMap<>();
    private final AdapterDiscoveryListener delegateRegistrar = adapter -> registerDelegate((DiscoveredAdapter) adapter);

    // proxy listeners
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private final List<GenericBluetoothDeviceListener> genericBluetoothDeviceListeners = new CopyOnWriteArrayList<>();
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new CopyOnWriteArrayList<>();

    // state bitmap fields
    private final ConcurrentBitMap ready = new ConcurrentBitMap();
    private final ConcurrentBitMap online = new ConcurrentBitMap();
    private final ConcurrentBitMap blocked = new ConcurrentBitMap();
    private final ConcurrentBitMap connected = new ConcurrentBitMap();
    private final ConcurrentBitMap servicesResolved = new ConcurrentBitMap();

    // common state fields
    private int bluetoothClass;
    private boolean bleEnabled;
    private String name;
    private String alias;
    private int onlineTimeout = DeviceGovernorImpl.DEFAULT_ONLINE_TIMEOUT;
    private short rssi;
    private KalmanFilterProxy rssiFilter = new KalmanFilterProxy();
    private boolean rssiFilteringEnabled = true;
    private long rssiReportingRate = DeviceGovernorImpl.DEFAULT_RSSI_REPORTING_RATE;
    private short measuredTxPower;
    private double signalPropagationExponent = DeviceGovernorImpl.DEFAULT_SIGNAL_PROPAGATION_EXPONENT;
    private Date lastChanged;

    // some specifics for the nearest adapter detection
    private final SortedSet<DeviceGovernorHandler> sortedByDistanceGovernors = new TreeSet<>(new DistanceComparator());
    private DeviceGovernor nearest;
    private final ReentrantLock rssiLock = new ReentrantLock();

    // controlling fields
    private boolean connectionControl;
    private boolean blockedControl;

    // combined governor specific fields
    private ConnectionStrategy connectionStrategy = ConnectionStrategy.NEAREST_ADAPTER;
    private URL preferredAdapter;
    private DeviceGovernor connectionTarget;

    CombinedDeviceGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    @Override
    public int getBluetoothClass() throws NotReadyException {
        return bluetoothClass;
    }

    @Override
    public boolean isBleEnabled() throws NotReadyException {
        return bleEnabled;
    }

    @Override
    public String getName() throws NotReadyException {
        return name != null ? name : url.getDeviceAddress();
    }

    @Override
    public String getAlias() throws NotReadyException {
        return alias;
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {
        governors.values().forEach(deviceGovernorHandler -> {
            if (deviceGovernorHandler.delegate.isReady()) {
                deviceGovernorHandler.delegate.setAlias(alias);
            }
        });
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        String alias = getAlias();
        return alias != null ? alias : getName();
    }

    @Override
    public boolean isConnected() throws NotReadyException {
        return connected.get();
    }

    @Override
    public boolean getConnectionControl() {
        return connectionControl;
    }

    @Override
    public void setConnectionControl(boolean connected) {
        logger.debug("Setting connection control: {} : {}", url, connectionControl);
        connectionControl = connected;
        if (connected) {
            updateConnectionTarget();
        } else {
            // make sure nothing sets connectionTarget and calls setConnectionControls
            synchronized (this.connected) {
                governors.values().forEach(deviceGovernorHandler -> deviceGovernorHandler.delegate
                        .setConnectionControl(false));
            }
        }
    }

    @Override
    public boolean isServicesResolved() {
        return servicesResolved.get();
    }

    @Override
    public List<GattService> getResolvedServices() throws NotReadyException {
        DeviceGovernor deviceGovernor = getGovernor(servicesResolved.getUniqueIndex());
        return deviceGovernor != null ? deviceGovernor.getResolvedServices() : null;
    }

    @Override
    public Map<Short, byte[]> getManufacturerData() {
        DeviceGovernor governor = nearest;
        return governor != null ? governor.getManufacturerData() : Collections.emptyMap();
    }

    @Override
    public Map<URL, byte[]> getServiceData() {
        DeviceGovernor governor = nearest;
        return governor != null ? governor.getServiceData() : Collections.emptyMap();
    }

    private void updateConnectionTarget() {
        logger.debug("Updating connection target: {} : {}", url, connectionControl);
        boolean connectionControl = this.connectionControl;
        // make sure nothing sets connectionTarget and calls setConnectionControls
        synchronized (this.connected) {
            if (!isConnected()) {
                DeviceGovernor newTarget = findConnectionTarget();
                logger.debug("Current target / new target: {} / {}",
                        connectionTarget != null ? connectionTarget.getURL() : null, newTarget.getURL());
                if (connectionTarget != null && !connectionTarget.equals(newTarget)) {
                    connectionTarget.setConnectionControl(false);
                }
                connectionTarget = newTarget;
                if (connectionTarget != null) {
                    connectionTarget.setConnectionControl(connectionControl);
                }
            } else {
                logger.debug("Skipping updating connection target as the governor is currently connected: {}", url);
            }
        }
    }

    private DeviceGovernor findConnectionTarget() {
        logger.debug("Finding connection target: {} : {}", url, connectionStrategy);
        switch (connectionStrategy) {
            case NEAREST_ADAPTER:
                logger.debug("Nearest connection target: {}", nearest.getURL());
                return nearest;
            case PREFERRED_ADAPTER:
                logger.debug("Preferred adapter: {}", preferredAdapter);
                if (preferredAdapter != null) {
                    DeviceGovernorHandler preferredHandler = governors.get(
                            preferredAdapter.copyWithProtocol(null).copyWithDevice(url.getDeviceAddress()));
                    if (preferredHandler != null) {
                        logger.debug("Preferred connection target: {}", preferredHandler.delegate.getURL());
                        return preferredHandler.delegate;
                    }
                }
                return null;
            default: throw new IllegalStateException("Unknown connection strategy: " + connectionStrategy);
        }
    }

    @Override
    public boolean isBlocked() throws NotReadyException {
        return blocked.get();
    }

    @Override
    public boolean getBlockedControl() {
        return blockedControl;
    }

    @Override
    public void setBlockedControl(boolean blocked) {
        blockedControl = blocked;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setBlockedControl(blocked));
    }

    @Override
    public boolean isOnline() {
        return online.get();
    }

    @Override
    public int getOnlineTimeout() {
        return onlineTimeout;
    }

    @Override
    public void setOnlineTimeout(int timeout) {
        onlineTimeout = timeout;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setOnlineTimeout(timeout));
    }

    @Override
    public short getRSSI() throws NotReadyException {
        return rssi;
    }

    @Override
    public long getLastAdvertised() {
        DeviceGovernor nearest = this.nearest;
        return nearest.getLastAdvertised();
    }

    @Override
    public short getTxPower() {
        return 0;
    }

    @Override
    public short getMeasuredTxPower() {
        return measuredTxPower;
    }

    @Override
    public void setMeasuredTxPower(short txPower) {
        measuredTxPower = txPower;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setMeasuredTxPower(txPower));
    }

    @Override
    public double getSignalPropagationExponent() {
        return signalPropagationExponent;
    }

    @Override
    public void setSignalPropagationExponent(double exponent) {
        signalPropagationExponent = exponent;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setSignalPropagationExponent(exponent));
    }

    @Override
    public double getEstimatedDistance() {
        DeviceGovernor governor = nearest;
        return governor != null ? governor.getEstimatedDistance() : 0.0;
    }

    @Override
    public URL getLocation() {
        DeviceGovernor governor = nearest;
        return !isConnected() && governor != null ? governor.getURL().getAdapterURL() : null;
    }

    @Override
    public void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener) {
        bluetoothSmartDeviceListeners.add(listener);
    }

    @Override
    public void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener) {
        bluetoothSmartDeviceListeners.remove(listener);
    }

    @Override
    public void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener) {
        genericBluetoothDeviceListeners.add(listener);
    }

    @Override
    public void removeGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener) {
        genericBluetoothDeviceListeners.remove(listener);
    }

    @Override
    public void addGovernorListener(GovernorListener listener) {
        governorListeners.add(listener);
    }

    @Override
    public void removeGovernorListener(GovernorListener listener) {
        governorListeners.remove(listener);
    }

    @Override
    public Map<URL, List<CharacteristicGovernor>> getServicesToCharacteristicsMap() throws NotReadyException {
        return null;
    }

    @Override
    public List<URL> getCharacteristics() throws NotReadyException {
        return null;
    }

    @Override
    public List<CharacteristicGovernor> getCharacteristicGovernors() throws NotReadyException {
        return null;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public BluetoothObjectType getType() {
        return BluetoothObjectType.DEVICE;
    }

    @Override
    public Date getLastActivity() {
        return lastChanged;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    @Override
    public void init() {
        logger.debug("Initializing combined device governor: {}", url);
        bluetoothManager.addAdapterDiscoveryListener(delegateRegistrar);
        bluetoothManager.getDiscoveredAdapters().forEach(this::registerDelegate);
        logger.debug("Combined device governor initialization performed: {}", url);
    }

    @Override
    public void update() {
        updateConnectionTarget();
    }

    @Override
    public void reset() { /* do nothing */ }

    @Override
    public void dispose() {
        logger.debug("Disposing combined device governor: {}", url);
        setConnectionControl(false);
        bluetoothManager.removeAdapterDiscoveryListener(delegateRegistrar);
        governors.values().forEach(DeviceGovernorHandler::dispose);
        governors.clear();
        governorListeners.clear();
        genericBluetoothDeviceListeners.clear();
        bluetoothSmartDeviceListeners.clear();
        sortedByDistanceGovernors.clear();
        logger.debug("Combined device governor disposed: {}", url);
    }

    @Override
    public void setRssiFilter(Class<? extends Filter<Short>> filter) {
        throw new IllegalStateException("Not supported by combined governor. ");
    }

    @Override
    public Filter<Short> getRssiFilter() {
        return rssiFilter;
    }

    @Override
    public boolean isRssiFilteringEnabled() {
        return rssiFilteringEnabled;
    }

    @Override
    public void setRssiFilteringEnabled(boolean enabled) {
        rssiFilteringEnabled = enabled;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setRssiFilteringEnabled(enabled));
    }

    @Override
    public void setRssiReportingRate(long rate) {
        rssiReportingRate = rate;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.delegate.setRssiReportingRate(rate));
    }

    @Override
    public long getRssiReportingRate() {
        return rssiReportingRate;
    }

    @Override
    public ConnectionStrategy getConnectionStrategy() {
        return connectionStrategy;
    }

    @Override
    public void setConnectionStrategy(ConnectionStrategy connectionStrategy) {
        if (connectionStrategy == null) {
            throw new IllegalArgumentException("Connection strategy cannot be null");
        }
        this.connectionStrategy = connectionStrategy;
    }

    @Override
    public URL getPreferredAdapter() {
        return preferredAdapter;
    }

    @Override
    public void setPreferredAdapter(URL preferredAdapter) {
        this.preferredAdapter = preferredAdapter;
    }

    @Override
    public URL getConnectedAdapter() {
        DeviceGovernor deviceGovernor = getGovernor(connected.getUniqueIndex());
        return isConnected() && deviceGovernor != null ? deviceGovernor.getURL() : null;
    }

    private DeviceGovernor getGovernor(int index) {
        return governors.values().stream().filter(handler -> handler.index == index)
                .map(handler ->handler.delegate).findFirst().orElse(null);
    }

    private void registerDelegate(DiscoveredAdapter adapter) {
        URL delegateURL = url.copyWithAdapter(adapter.getURL().getAdapterAddress());
        logger.debug("Delegates number: {}. A new delegate offered: {}.", governors.size(), delegateURL);
        synchronized (governors) {
            registerDelegate(delegateURL);
        }
    }

    private void registerDelegate(URL url) {
        if (url.isDevice() && this.url.getDeviceAddress().equals(url.getDeviceAddress())
                && !COMBINED_ADDRESS.equals(url.getAdapterAddress())) {
            if (governorsCount.get() > 63) {
                throw new IllegalStateException("Combined Device Governor can only span up to 63 device governors "
                        + "(adapters).");
            }
            governors.computeIfAbsent(url.copyWithProtocol(null), newUrl -> {
                DeviceGovernor deviceGovernor = bluetoothManager.getDeviceGovernor(url);
                int index = governorsCount.getAndIncrement();
                logger.debug("Registering a new delegate: {} : {}", newUrl, index);
                DeviceGovernorHandler handler = new DeviceGovernorHandler(deviceGovernor, index);
                handler.init();
                return handler;
            });
        }
    }

    private void updateLastUpdated(Date lastActivity) {
        if (lastChanged == null || lastChanged.before(lastActivity)) {
            lastChanged = lastActivity;
            BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                listener.lastUpdatedChanged(lastActivity);
            }, logger, "Execution error of a governor listener: last changed");
        }
    }

    private void updateRssi(short newRssi) {
        rssi = newRssi;
        BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
            listener.rssiChanged(newRssi);
        }, logger, "Execution error of a RSSI listener");
    }

    private final class DeviceGovernorHandler
        implements GovernorListener, BluetoothSmartDeviceListener, GenericBluetoothDeviceListener {

        private final DeviceGovernor delegate;
        private final int index;
        private double distance = Double.MAX_VALUE;
        private long lastAdvertised;
        private boolean inited;

        private DeviceGovernorHandler(DeviceGovernor delegate, int index) {
            this.delegate = delegate;
            this.index = index;
        }

        private void init() {
            initSafe();
            initUnsafe();
        }

        private void initSafe() {
            logger.debug("Initializing safe operations: {}", delegate.getURL());
            // safe operations
            delegate.addBluetoothSmartDeviceListener(this);
            delegate.addGenericBluetoothDeviceListener(this);
            delegate.addGovernorListener(this);

            notifyIfChangedOnline(delegate.isOnline());

            if (!(delegate.getRssiFilter() instanceof RssiKalmanFilter)) {
                delegate.setRssiFilter(RssiKalmanFilter.class);
            }
            delegate.setOnlineTimeout(onlineTimeout);
            delegate.setBlockedControl(blockedControl);
            delegate.setRssiFilteringEnabled(rssiFilteringEnabled);
            delegate.setRssiReportingRate(rssiReportingRate);
            delegate.setSignalPropagationExponent(signalPropagationExponent);
            delegate.setMeasuredTxPower(measuredTxPower);

            Date lastActivity = delegate.getLastActivity();
            if (lastActivity != null) {
                updateLastUpdated(lastActivity);
            }
        }

        private void initUnsafe() {
            if (!inited) {
                // this method can be called by different threads (notifications) so the synchronization is needed
                synchronized (delegate) {
                    // unsafe operations
                    boolean delegateReady = delegate.isReady();
                    logger.debug("Initializing unsafe operations: {} : {}", delegate.getURL(), delegateReady);
                    if (delegateReady) {
                        notifyIfChangedReady(true);
                        // any of the following operations can produce NotReadyException
                        try {
                            notifyIfChangedBlocked(delegate.isBlocked());
                            notifyIfChangedConnected(delegate.isConnected());
                            if (delegate.isServicesResolved()) {
                                servicesResolved(delegate.getResolvedServices());
                            }

                            int deviceBluetoothClass = delegate.getBluetoothClass();
                            if (deviceBluetoothClass != 0) {
                                bluetoothClass = deviceBluetoothClass;
                            }
                            bleEnabled |= delegate.isBleEnabled();

                            name = delegate.getName();
                            String deviceAlias = delegate.getAlias();
                            if (deviceAlias != null) {
                                alias = deviceAlias;
                            }
                            rssiChanged(delegate.getRSSI());
                            inited = true;
                            logger.debug("Initializing unsafe operations successfully completed: {}",
                                    delegate.getURL());
                        } catch (NotReadyException ex) {
                            // the device has become not ready, that's fine it will be initialized again later
                            // when it becomes ready, so just ignore it for now
                            logger.debug("Error occurred while initializing unsafe operations: {} : {}",
                                    url, ex.getMessage());
                        }
                    }
                }
            }
        }

        @Override
        public void connected() {
            notifyIfChangedConnected(true);
        }

        @Override
        public void disconnected() {
            notifyIfChangedConnected(false);
        }

        @Override
        public void servicesResolved(List<GattService> gattServices) {
            logger.debug("Services resolved (listener): {} : {}", url, gattServices.size());
            servicesResolved.exclusiveSet(index, true,
                () -> {
                    notifyServicesResolved(gattServices);
                }, () -> {
                    notifyServicesUnresolved();
                    notifyServicesResolved(gattServices);
                }
            );
        }

        @Override
        public void servicesUnresolved() {
            logger.debug("Services unresolved (listener): {}", url);
            servicesResolved.exclusiveSet(index, false, () -> {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                    BluetoothSmartDeviceListener::servicesUnresolved,
                    logger, "Execution error of a service resolved listener");
            });
        }

        @Override
        public void serviceDataChanged(Map<URL, byte[]> serviceData) {
            logger.debug("Services data changed (listener): {} : {} : {}",
                    url, serviceData.size(), delegate == nearest);
            if (delegate == nearest) {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                        listener -> listener.serviceDataChanged(serviceData.entrySet().stream()
                                .collect(Collectors.toMap(entry -> entry.getKey().copyWithAdapter(COMBINED_ADDRESS),
                                        Map.Entry::getValue))),
                        logger, "Execution error of a service resolved listener");
            }
        }

        @Override
        public void manufacturerDataChanged(Map<Short, byte[]> manufacturerData) {
            logger.debug("Manufacturer data changed (listener): {} : {} : {}",
                    url, manufacturerData.size(), delegate == nearest);
            if (delegate == nearest) {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                        listener -> listener.manufacturerDataChanged(manufacturerData),
                        logger, "Execution error of a service resolved listener");
            }
        }

        @Override
        public void online() {
            notifyIfChangedOnline(true);
        }

        @Override
        public void offline() {
            sortedByDistanceGovernors.remove(this);
            notifyIfChangedOnline(false);
        }

        @Override
        public void blocked(boolean newState) {
            notifyIfChangedBlocked(newState);
        }

        @Override
        public void rssiChanged(short newRssi) {
            try {
                if (rssiLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    try {
                        sortedByDistanceGovernors.remove(this);
                        lastAdvertised = delegate.getLastAdvertised();
                        distance = delegate.getEstimatedDistance();
                        sortedByDistanceGovernors.add(this);
                        DeviceGovernor newNearest = sortedByDistanceGovernors.first().delegate;
                        logger.debug("Calculating nearest delegate (current / new): {} / {}",
                                nearest != null ? nearest.getURL() : null, newNearest.getURL());
                        nearest = newNearest;
                        if (delegate == nearest) {
                            updateRssi(newRssi);
                        }
                    } finally {
                        rssiLock.unlock();
                    }
                }
            } catch (InterruptedException ignore) {
                logger.debug("Could not acquire a lock to update RSSI: {}", delegate.getURL());
            }
        }

        @Override
        public void ready(boolean isReady) {
            logger.debug("Delegate changed ready state: {} : {}", delegate.getURL(), isReady);
            if (isReady) {
                initUnsafe();
            } else {
                sortedByDistanceGovernors.remove(this);
            }
            notifyIfChangedReady(isReady);
        }

        @Override
        public void lastUpdatedChanged(Date lastActivity) {
            updateLastUpdated(lastActivity);
        }

        private void dispose() {
            logger.debug("Disposing delegate: {}", delegate.getURL());
            delegate.removeBluetoothSmartDeviceListener(this);
            delegate.removeGenericBluetoothDeviceListener(this);
            delegate.removeGovernorListener(this);
        }

        private void notifyIfChangedOnline(boolean newState) {
            logger.debug("Setting online: {} : {} / {}", url, online.get(), newState);
            online.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
                    if (newState) {
                        listener.online();
                    } else {
                        listener.offline();
                    }
                }, logger, "Execution error of an online listener");
            });
        }

        private void notifyIfChangedReady(boolean newState) {
            logger.debug("Setting ready: {} : {} / {}", url, ready.get(), newState);
            ready.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                    listener.ready(newState);
                }, logger, "Execution error of a governor listener: ready");
            });
        }

        private void notifyIfChangedConnected(boolean newState) {
            logger.debug("Setting connected: {} : {} / {}", url, connected.get(), newState);
            connected.exclusiveSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners, listener -> {
                    if (newState) {
                        listener.connected();
                    } else {
                        listener.disconnected();
                    }
                }, logger, "Execution error of a connection listener");
            });
        }

        private void notifyIfChangedBlocked(boolean newState) {
            logger.debug("Setting blocked: {} : {} / {}", url, blocked.get(), newState);
            blocked.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
                    listener.blocked(newState);
                }, logger, "Execution error of a Blocked listener");
            });
        }

        private void notifyServicesResolved(List<GattService> services) {
            logger.debug("Notify service resolved: {} : {} : {}",
                    url, bluetoothSmartDeviceListeners.size(), services.size());
            List<GattService> combinedServices = new ArrayList<>(services.size());
            services.forEach(service -> {
                List<GattCharacteristic> combinedCharacteristics =
                        new ArrayList<>(service.getCharacteristics().size());

                service.getCharacteristics().forEach(characteristic -> {
                    GattCharacteristic combinedCharacteristic = new GattCharacteristic(
                            characteristic.getURL().copyWithProtocol(null).copyWithAdapter(COMBINED_ADDRESS),
                            characteristic.getFlags());
                    combinedCharacteristics.add(combinedCharacteristic);
                });

                GattService combinedService = new GattService(
                        service.getURL().copyWithProtocol(null).copyWithAdapter(COMBINED_ADDRESS),
                        combinedCharacteristics);
                combinedServices.add(combinedService);
            });
            BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners, listener -> {
                listener.servicesResolved(combinedServices);
            }, logger, "Execution error of a service resolved listener");
        }

        private void notifyServicesUnresolved() {
            logger.debug("Notify service resolved: {} : {}", url, bluetoothSmartDeviceListeners.size());
            BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                BluetoothSmartDeviceListener::servicesUnresolved,
                logger, "Execution error of a service resolved listener");
        }
    }

    private class KalmanFilterProxy extends RssiKalmanFilter {

        @Override
        public void setProcessNoise(double processNoise) {
            super.setProcessNoise(processNoise);
            forEachKalmanFilter(filter -> filter.setProcessNoise(processNoise));
        }

        @Override
        public void setMeasurementNoise(double measurementNoise) {
            super.setMeasurementNoise(measurementNoise);
            forEachKalmanFilter(filter -> filter.setMeasurementNoise(measurementNoise));
        }

        private void forEachKalmanFilter(Consumer<RssiKalmanFilter> consumer) {
            governors.values().stream()
                    .filter(governorHandler -> governorHandler.delegate
                            .getRssiFilter() instanceof RssiKalmanFilter)
                    .map(governorHandler -> (RssiKalmanFilter) governorHandler.delegate.getRssiFilter())
                    .forEach(consumer);
        }
    }

    private class DistanceComparator implements Comparator<DeviceGovernorHandler> {

        @Override
        public int compare(DeviceGovernorHandler first, DeviceGovernorHandler second) {
            long current = System.currentTimeMillis();
            boolean firstStale = current - first.lastAdvertised > STALE_TIMEOUT;
            boolean secondStale = current - second.lastAdvertised > STALE_TIMEOUT;
            double firstWeighedValue = first.distance * (firstStale ? 1 : 1000);
            double secondWeighedValue = second.distance * (secondStale ? 1 : 1000);

            return Double.compare(firstWeighedValue, secondWeighedValue);
        }
    }
}
