package org.sputnikdev.bluetooth.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SharedAdapterGovernorImpl implements AdapterGovernor, BluetoothObjectGovernor, AdapterDiscoveryListener {

    private Logger logger = LoggerFactory.getLogger(SharedAdapterGovernorImpl.class);

    private final Map<URL, AdapterGovernorHandler> governors = new ConcurrentHashMap<>();
    private final BluetoothManagerImpl bluetoothManager;
    private final URL url;

    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private final List<AdapterListener> adapterListeners = new CopyOnWriteArrayList<>();

    private Date lastChanged;

    private boolean poweredControl = true;
    private boolean discoveringControl = true;
    private double signalPropagationExponent;

    private final AtomicLong ready = new AtomicLong();
    private final AtomicLong powered = new AtomicLong();
    private final AtomicLong discovering = new AtomicLong();

    private final AtomicInteger governorsCount = new AtomicInteger();

    SharedAdapterGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
        this.bluetoothManager.addAdapterDiscoveryListener(this);
        this.bluetoothManager.getRegisteredGovernors().forEach(this::registerGovernor);
        this.bluetoothManager.getDiscoveredAdapters().stream().map(DiscoveredAdapter::getURL)
            .forEach(this::registerGovernor);
    }

    @Override
    public String getName() throws NotReadyException {
        return "Shared Bluetooth Adapter";
    }

    @Override
    public String getAlias() throws NotReadyException {
        return null;
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {

    }

    @Override
    public String getDisplayName() throws NotReadyException {
        return getName();
    }

    @Override
    public boolean isPowered() throws NotReadyException {
        return powered.get() > 0;
    }

    @Override
    public boolean getPoweredControl() {
        return poweredControl;
    }

    @Override
    public void setPoweredControl(boolean powered) {
        poweredControl = powered;
        governors.values().forEach(
            adapterGovernorHandler -> adapterGovernorHandler.adapterGovernor.setPoweredControl(powered));
    }

    @Override
    public boolean isDiscovering() throws NotReadyException {
        return discovering.get() > 0;
    }

    @Override
    public boolean getDiscoveringControl() {
        return discoveringControl;
    }

    @Override
    public void setDiscoveringControl(boolean discovering) {
        discoveringControl = discovering;
        governors.values().forEach(
            adapterGovernorHandler -> adapterGovernorHandler.adapterGovernor.setDiscoveringControl(discovering));
    }

    @Override
    public double getSignalPropagationExponent() {
        return signalPropagationExponent;
    }

    @Override
    public void setSignalPropagationExponent(double exponent) {
        signalPropagationExponent = exponent;
        governors.values().forEach(adapterGovernorHandler -> adapterGovernorHandler.adapterGovernor
                .setSignalPropagationExponent(exponent));
    }

    @Override
    public List<URL> getDevices() throws NotReadyException {
        return null;
    }

    @Override
    public List<DeviceGovernor> getDeviceGovernors() throws NotReadyException {
        return null;
    }

    @Override
    public void update() { /* do nothing */}

    @Override
    public void reset() { /* do nothing */}

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
        return BluetoothObjectType.ADAPTER;
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
    public void addAdapterListener(AdapterListener adapterListener) {
        adapterListeners.add(adapterListener);
    }

    @Override
    public void removeAdapterListener(AdapterListener adapterListener) {
        adapterListeners.remove(adapterListener);
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
    public void discovered(DiscoveredAdapter adapter) {
        registerGovernor(adapter.getURL());
    }

    @Override
    public void adapterLost(URL address) { /* do nothing */ }

    private void registerGovernor(URL url) {
        if (governorsCount.get() > 63) {
            throw new IllegalStateException("Shared Device Governor can only span upto 63 device governors.");
        }
        if (url.isAdapter() && !url.equals(this.url)) {
            governors.computeIfAbsent(url, newUrl -> {
                AdapterGovernor deviceGovernor = BluetoothManagerFactory.getManager().getAdapterGovernor(url);
                return new AdapterGovernorHandler(deviceGovernor, governorsCount.getAndIncrement());
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

    private final class AdapterGovernorHandler implements GovernorListener, AdapterListener {

        private final AdapterGovernor adapterGovernor;
        private final int index;

        private AdapterGovernorHandler(AdapterGovernor adapterGovernor, int index) {
            this.adapterGovernor = adapterGovernor;
            this.index = index;
            this.adapterGovernor.addAdapterListener(this);
            this.adapterGovernor.addGovernorListener(this);
            this.adapterGovernor.setPoweredControl(poweredControl);
            this.adapterGovernor.setDiscoveringControl(discoveringControl);
            this.adapterGovernor.setSignalPropagationExponent(signalPropagationExponent);
            ready(true);
        }

        @Override
        public void powered(boolean newState) {
            BluetoothManagerUtils.setState(powered, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(adapterListeners, listener -> {
                    listener.powered(newState);
                }, logger, "Execution error of a Powered listener");
            });
        }

        @Override
        public void discovering(boolean newState) {
            BluetoothManagerUtils.setState(discovering, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(adapterListeners, listener -> {
                    listener.discovering(newState);
                }, logger, "Execution error of a Discovering listener");
            });
        }

        @Override
        public void ready(boolean newState) {
            BluetoothManagerUtils.setState(ready, index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                    listener.ready(newState);
                }, logger, "Execution error of a governor listener: ready");
            });
        }

        @Override
        public void lastUpdatedChanged(Date lastActivity) {
            updateLastUpdated(lastActivity);
        }

    }

}
