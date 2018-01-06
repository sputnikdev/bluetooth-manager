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
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.CombinedDeviceGovernor;
import org.sputnikdev.bluetooth.manager.ConnectionStrategy;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.GattCharacteristic;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

class CombinedDeviceGovernorImpl implements DeviceGovernor, CombinedDeviceGovernor,
        BluetoothObjectGovernor, DeviceDiscoveryListener {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernorImpl.class);

    private final BluetoothManagerImpl bluetoothManager;
    private final URL url;

    private final AtomicInteger governorsCount = new AtomicInteger();
    private final Map<URL, DeviceGovernorHandler> governors = new ConcurrentHashMap<>();

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
    private int onlineTimeout;
    private short rssi;
    private KalmanFilterProxy rssiFilter = new KalmanFilterProxy();
    private boolean rssiFilteringEnabled = true;
    private long rssiReportingRate = 1000;
    private short measuredTxPower;
    private double signalPropagationExponent = DeviceGovernorImpl.DEAFULT_SIGNAL_PROPAGATION_EXPONENT;
    private Date lastChanged;

    // some specifics for the nearest adapter detection
    private final SortedSet<DeviceGovernorHandler> sortedByDistanceGovernors =
            new TreeSet<>(Comparator.comparingDouble(handler -> handler.distance));
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
            if (deviceGovernorHandler.deviceGovernor.isReady()) {
                deviceGovernorHandler.deviceGovernor.setAlias(alias);
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
        connectionControl = connected;
        if (connected) {
            updateConnectionTarget();
        } else {
            // make sure nothing sets connectionTarget and calls setConnectionControls
            synchronized (this.connected) {
                governors.values().forEach(deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor
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

    private void updateConnectionTarget() {
        boolean connectionControl = this.connectionControl;
        // make sure nothing sets connectionTarget and calls setConnectionControls
        synchronized (connected) {
            if (!isConnected()) {
                DeviceGovernor newTarget = determineConnectionTarget();
                if (connectionTarget != null && !connectionTarget.equals(newTarget)) {
                    connectionTarget.setConnectionControl(false);
                }
                connectionTarget = newTarget;
                if (connectionTarget != null) {
                    connectionTarget.setConnectionControl(connectionControl);
                }
            }
        }
    }

    private DeviceGovernor determineConnectionTarget() {
        switch (connectionStrategy) {
            case NEAREST_ADAPTER:
                return nearest;
            case PREFERRED_ADAPTER:
                if (preferredAdapter != null) {
                    DeviceGovernorHandler preferredHandler = governors.get(
                            preferredAdapter.copyWithProtocol(null).copyWithDevice(url.getDeviceAddress()));
                    if (preferredHandler != null) {
                        return preferredHandler.deviceGovernor;
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
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setBlockedControl(blocked));
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
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setOnlineTimeout(timeout));
    }

    @Override
    public short getRSSI() throws NotReadyException {
        return rssi;
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
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setMeasuredTxPower(txPower));
    }

    @Override
    public double getSignalPropagationExponent() {
        return signalPropagationExponent;
    }

    @Override
    public void setSignalPropagationExponent(double exponent) {
        signalPropagationExponent = exponent;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setSignalPropagationExponent(exponent));
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
    public void discovered(DiscoveredDevice discoveredDevice) {
        registerGovernor(discoveredDevice.getURL());
    }

    @Override
    public void deviceLost(URL url) { /* do nothing */ }

    @Override
    public void init() {
        CompletableFuture.runAsync(() -> {
            bluetoothManager.getRegisteredGovernors().forEach(this::registerGovernor);
            bluetoothManager.getDiscoveredDevices().stream().map(DiscoveredDevice::getURL)
                    .forEach(this::registerGovernor);
            bluetoothManager.addDeviceDiscoveryListener(this);
        });
    }

    @Override
    public void update() {
        updateConnectionTarget();
    }

    @Override
    public void reset() { /* do nothing */ }

    @Override
    public void dispose() {
        setConnectionControl(false);
        bluetoothManager.removeDeviceDiscoveryListener(this);
        governors.values().forEach(DeviceGovernorHandler::dispose);
        governors.clear();
        governorListeners.clear();
        genericBluetoothDeviceListeners.clear();
        bluetoothSmartDeviceListeners.clear();
        sortedByDistanceGovernors.clear();
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
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setRssiFilteringEnabled(enabled));
    }

    @Override
    public void setRssiReportingRate(long rate) {
        rssiReportingRate = rate;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setRssiReportingRate(rate));
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
                .map(handler ->handler.deviceGovernor).findFirst().orElse(null);
    }

    private void registerGovernor(URL url) {
        if (governorsCount.get() > 63) {
            throw new IllegalStateException("Combined Device Governor can only span up to 63 device governors.");
        }
        if (url.isDevice() && this.url.getDeviceAddress().equals(url.getDeviceAddress())
                && !COMBINED_ADDRESS.equals(url.getAdapterAddress())) {
            governors.computeIfAbsent(url.copyWithProtocol(null), newUrl -> {
                DeviceGovernor deviceGovernor = BluetoothManagerFactory.getManager().getDeviceGovernor(url);
                int index = governorsCount.getAndIncrement();
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

        private final DeviceGovernor deviceGovernor;
        private final int index;
        private double distance = Double.MAX_VALUE;
        private boolean inited;

        private DeviceGovernorHandler(DeviceGovernor deviceGovernor, int index) {
            this.deviceGovernor = deviceGovernor;
            this.index = index;
        }

        private void init() {
            initSafe();
            initUnsafe();
        }

        private void initSafe() {
            // safe operations
            deviceGovernor.addBluetoothSmartDeviceListener(this);
            deviceGovernor.addGenericBluetoothDeviceListener(this);
            deviceGovernor.addGovernorListener(this);

            if (!(deviceGovernor.getRssiFilter() instanceof RssiKalmanFilter)) {
                deviceGovernor.setRssiFilter(RssiKalmanFilter.class);
            }
            deviceGovernor.setOnlineTimeout(onlineTimeout);
            deviceGovernor.setBlockedControl(blockedControl);
            deviceGovernor.setRssiFilteringEnabled(rssiFilteringEnabled);
            deviceGovernor.setRssiReportingRate(rssiReportingRate);
            deviceGovernor.setSignalPropagationExponent(signalPropagationExponent);
            deviceGovernor.setMeasuredTxPower(measuredTxPower);

            Date lastActivity = deviceGovernor.getLastActivity();
            if (lastActivity != null) {
                updateLastUpdated(lastActivity);
            }

            notifyIfChangedOnline(deviceGovernor.isOnline());
        }

        private void initUnsafe() {
            if (!inited) {
                // this method can be called by different threads (notifications) so the synchronization is needed
                synchronized (deviceGovernor) {
                    // unsafe operations
                    if (deviceGovernor.isReady()) {
                        notifyIfChangedReady(true);
                        // any of the following operations can produce NotReadyException
                        try {
                            notifyIfChangedBlocked(deviceGovernor.isBlocked());
                            notifyIfChangedConnected(deviceGovernor.isConnected());
                            if (deviceGovernor.isServicesResolved()) {
                                servicesResolved(deviceGovernor.getResolvedServices());
                            }

                            int deviceBluetoothClass = deviceGovernor.getBluetoothClass();
                            if (deviceBluetoothClass != 0) {
                                bluetoothClass = deviceBluetoothClass;
                            }
                            bleEnabled |= deviceGovernor.isBleEnabled();

                            name = deviceGovernor.getName();
                            String deviceAlias = deviceGovernor.getAlias();
                            if (deviceAlias != null) {
                                alias = deviceAlias;
                            }
                            inited = true;
                        } catch (NotReadyException ex) {
                            // the device has become not ready, that's fine it will be initialized again later
                            // when it becomes ready, so just ignore it for now
                            logger.debug("Could not initialize device governor handler", ex);
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
            servicesResolved.exclusiveSet(index, false, () -> {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                    BluetoothSmartDeviceListener::servicesUnresolved,
                    logger, "Execution error of a service resolved listener");
            });
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
                        distance = deviceGovernor.getEstimatedDistance();
                        sortedByDistanceGovernors.add(this);
                        nearest = sortedByDistanceGovernors.first().deviceGovernor;
                        if (deviceGovernor == nearest) {
                            updateRssi(newRssi);
                        }
                    } finally {
                        rssiLock.unlock();
                    }
                }
            } catch (InterruptedException ignore) {
                logger.warn("Could not aquire a lock to update RSSI");
            }
        }

        @Override
        public void ready(boolean isReady) {
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
            deviceGovernor.removeBluetoothSmartDeviceListener(this);
            deviceGovernor.removeGenericBluetoothDeviceListener(this);
            deviceGovernor.removeGovernorListener(this);
        }

        private void notifyIfChangedOnline(boolean newState) {
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
            ready.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                    listener.ready(newState);
                }, logger, "Execution error of a governor listener: ready");
            });
        }

        private void notifyIfChangedConnected(boolean newState) {
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
            blocked.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
                    listener.blocked(newState);
                }, logger, "Execution error of a Blocked listener");
            });
        }

        private void notifyServicesResolved(List<GattService> services) {
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
                    .filter(governorHandler -> governorHandler.deviceGovernor
                            .getRssiFilter() instanceof RssiKalmanFilter)
                    .map(governorHandler -> (RssiKalmanFilter) governorHandler.deviceGovernor.getRssiFilter())
                    .forEach(consumer);
        }
    }

}
