package org.sputnikdev.bluetooth.manager.impl;

import com.google.common.collect.EvictingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.Filter;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SharedDeviceGovernorImpl implements DeviceGovernor, BluetoothObjectGovernor, DeviceDiscoveryListener {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernorImpl.class);

    private final BluetoothManagerImpl bluetoothManager;
    private final URL url;
    private final Map<URL, DeviceGovernorHandler> governors = new ConcurrentHashMap<>();
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private final List<GenericBluetoothDeviceListener> genericBluetoothDeviceListeners = new CopyOnWriteArrayList<>();
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new CopyOnWriteArrayList<>();

    private final AtomicLong ready = new AtomicLong();
    private final AtomicLong online = new AtomicLong();
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong connected = new AtomicLong();
    private final AtomicLong servicesResolved = new AtomicLong();

    private final AtomicInteger governorsCount = new AtomicInteger();

    private int bluetoothClass;
    private boolean bleEnabled;
    private String name;
    private String alias;
    private int onlineTimeout;
    private short rssi;
    private boolean rssiFilteringEnabled = true;
    private long rssiReportingRate = 1000;
    private Date lastChanged;

    private boolean connectionControl;
    private boolean blockedControl;

    SharedDeviceGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
        this.bluetoothManager.addDeviceDiscoveryListener(this);
        this.bluetoothManager.getRegisteredGovernors().forEach(this::registerGovernor);
        this.bluetoothManager.getDiscoveredDevices().stream().map(DiscoveredDevice::getURL)
            .forEach(this::registerGovernor);
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
        return connected.get() > 0;
    }

    @Override
    public boolean getConnectionControl() {
        return connectionControl;
    }

    @Override
    public void setConnectionControl(boolean connected) {
        connectionControl = connected;
        governors.values().forEach(
            deviceGovernorHandler -> deviceGovernorHandler.deviceGovernor.setConnectionControl(connected));
    }

    @Override
    public boolean isBlocked() throws NotReadyException {
        return blocked.get() > 0;
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
        return online.get() > 0;
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
        return ready.get() > 0;
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
    public void update() { /* do nothing */ }

    @Override
    public void reset() { /* do nothing */ }

    @Override
    public void setRssiFilter(Filter<Short> filter) {
        throw new IllegalStateException("Not supported by group governor");
    }

    @Override
    public Filter<Short> getRssiFilter() {
        return null;
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

    private void registerGovernor(URL url) {
        if (governorsCount.get() > 63) {
            throw new IllegalStateException("Shared Device Governor can only span upto 63 device governors.");
        }
        if (url.isDevice() && this.url.getDeviceAddress().equals(url.getDeviceAddress())) {
            governors.computeIfAbsent(url, newUrl -> {
                DeviceGovernor deviceGovernor = BluetoothManagerFactory.getManager().getDeviceGovernor(url);
                return new DeviceGovernorHandler(deviceGovernor, governorsCount.getAndIncrement());
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

        Lock rssiUpdateLock = new ReentrantLock();
        private Queue<Short> rssi = EvictingQueue.create(10);
        private short rssiAverage;

        private DeviceGovernorHandler(DeviceGovernor deviceGovernor, int index) {
            this.deviceGovernor = deviceGovernor;
            this.index = index;
            this.deviceGovernor.addBluetoothSmartDeviceListener(this);
            this.deviceGovernor.addGenericBluetoothDeviceListener(this);
            this.deviceGovernor.addGovernorListener(this);

            if (deviceGovernor.isReady()) {
                name = deviceGovernor.getName();
                int deviceBluetoothClass = deviceGovernor.getBluetoothClass();
                if (deviceBluetoothClass != 0) {
                    bluetoothClass = deviceBluetoothClass;
                }
                bleEnabled |= deviceGovernor.isBleEnabled();

                String deviceAlias = deviceGovernor.getAlias();
                if (deviceAlias != null) {
                    alias = deviceAlias;
                }
                updateRssi(deviceGovernor.getRSSI());
                ready(true);
            }
            deviceGovernor.setOnlineTimeout(onlineTimeout);
            deviceGovernor.setConnectionControl(connectionControl);
            deviceGovernor.setBlockedControl(blockedControl);
            deviceGovernor.setRssiFilteringEnabled(rssiFilteringEnabled);
            deviceGovernor.setRssiReportingRate(rssiReportingRate);

            Date lastActivity = deviceGovernor.getLastActivity();
            if (lastActivity != null) {
                updateLastUpdated(lastActivity);
            }
        }

        @Override
        public void connected() {
            notifyConnected(true);
        }

        @Override
        public void disconnected() {
            notifyConnected(false);
        }

        @Override
        public void servicesResolved(List<GattService> gattServices) {
            BluetoothManagerUtils.setState(servicesResolved, index, true,
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
            BluetoothManagerUtils.setState(servicesResolved, index, false, () -> {
                BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                    BluetoothSmartDeviceListener::servicesUnresolved,
                    logger, "Execution error of a service resolved listener");
            });
        }

        @Override
        public void online() {
            notifyOnline(true);
        }

        @Override
        public void offline() {
            notifyOnline(false);
        }

        @Override
        public void blocked(boolean newState) {
            BluetoothManagerUtils.setState(blocked, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
                    listener.blocked(newState);
                }, logger, "Execution error of a Blocked listener");
            });
        }

        @Override
        public void rssiChanged(short newRssi) {
            if (rssiUpdateLock.tryLock()) {
                try {
                    // the device reports RSSI too fast that we can't handle it, so we skip some readings
                    rssi.add(newRssi);
                    short average = (short) rssi.stream().mapToInt(Short::intValue).average().orElse(0);
                    if (rssiAverage != average) {
                        updateRssi(average);
                    }
                    rssiAverage = average;
                } finally {
                    rssiUpdateLock.unlock();
                }
            }
        }

        @Override
        public void ready(boolean isReady) {
            notifyReady(isReady);
        }

        @Override
        public void lastUpdatedChanged(Date lastActivity) {
            updateLastUpdated(lastActivity);
        }

        private void notifyOnline(boolean newState) {
            BluetoothManagerUtils.setState(online, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(genericBluetoothDeviceListeners, listener -> {
                    if (newState) {
                        listener.online();
                    } else {
                        listener.offline();
                    }
                }, logger, "Execution error of an online listener");
            });
        }

        private void notifyReady(boolean newState) {
            BluetoothManagerUtils.setState(ready, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                    listener.ready(newState);
                }, logger, "Execution error of a governor listener: ready");
            });
        }

        private void notifyConnected(boolean connected) {
            BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners, listener -> {
                if (connected) {
                    listener.connected();
                } else {
                    listener.disconnected();
                }
            }, logger, "Execution error of a connection listener");
        }

        private void notifyServicesResolved(List<GattService> services) {
            BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners, listener -> {
                listener.servicesResolved(services);
            }, logger, "Execution error of a service resolved listener");
        }

        private void notifyServicesUnresolved() {
            BluetoothManagerUtils.safeForEachError(bluetoothSmartDeviceListeners,
                BluetoothSmartDeviceListener::servicesUnresolved,
                logger, "Execution error of a service resolved listener");
        }
    }

}
