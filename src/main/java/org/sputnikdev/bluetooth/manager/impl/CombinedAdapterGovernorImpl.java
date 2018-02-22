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
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class CombinedAdapterGovernorImpl implements AdapterGovernor, CombinedGovernor,
        BluetoothObjectGovernor, AdapterDiscoveryListener {

    private Logger logger = LoggerFactory.getLogger(CombinedAdapterGovernorImpl.class);

    private final Map<URL, AdapterGovernorHandler> governors = new ConcurrentHashMap<>();
    private final CompletableFutureService<BluetoothObjectGovernor> readyService = new CompletableFutureService<>();
    private final BluetoothManagerImpl bluetoothManager;
    private final URL url;

    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private final List<AdapterListener> adapterListeners = new CopyOnWriteArrayList<>();

    private Instant lastInteracted;

    private boolean poweredControl = true;
    private boolean discoveringControl = true;
    private double signalPropagationExponent;

    private final ConcurrentBitMap ready = new ConcurrentBitMap();
    private final ConcurrentBitMap powered = new ConcurrentBitMap();
    private final ConcurrentBitMap discovering = new ConcurrentBitMap();

    private final AtomicInteger governorsCount = new AtomicInteger();

    CombinedAdapterGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    @Override
    public String getName() throws NotReadyException {
        return "Combined Bluetooth Adapter";
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
        return powered.get();
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
        return discovering.get();
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
    public void init() {
        bluetoothManager.addAdapterDiscoveryListener(this);
        bluetoothManager.getRegisteredGovernors().forEach(this::registerGovernor);
        bluetoothManager.getDiscoveredAdapters().stream().map(DiscoveredAdapter::getURL)
                .forEach(this::registerGovernor);
    }

    @Override
    public void update() { /* do nothing */ }

    @Override
    public void reset() { /* do nothing */ }

    @Override
    public void dispose() {
        bluetoothManager.removeAdapterDiscoveryListener(this);
        governors.clear();
        governorListeners.clear();
        adapterListeners.clear();
        readyService.clear();
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
        return BluetoothObjectType.ADAPTER;
    }

    @Override
    public Instant getLastInteracted() {
        return lastInteracted;
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

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <G extends BluetoothGovernor, V> CompletableFuture<V> whenReady(Function<G, V> function) {
        return readyService.submit(this, (Function<BluetoothObjectGovernor, V>) function);
    }

    private void registerGovernor(URL url) {
        if (governorsCount.get() > 63) {
            throw new IllegalStateException("Shared Device Governor can only span upto 63 device governors.");
        }
        if (url.isAdapter() && !url.equals(this.url)) {
            governors.computeIfAbsent(url, newUrl -> {
                AdapterGovernor deviceGovernor = bluetoothManager.getAdapterGovernor(url);
                return new AdapterGovernorHandler(deviceGovernor, governorsCount.getAndIncrement());
            });
        }
    }

    private void updateLastInteracted(Instant lastActivity) {
        if (lastInteracted == null || lastInteracted.isBefore(lastActivity)) {
            lastInteracted = lastActivity;
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
            powered.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(adapterListeners, listener -> {
                    listener.powered(newState);
                }, logger, "Execution error of a Powered listener");
            });
        }

        @Override
        public void discovering(boolean newState) {
            discovering.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(adapterListeners, listener -> {
                    listener.discovering(newState);
                }, logger, "Execution error of a Discovering listener");
            });
        }

        @Override
        public void ready(boolean newState) {
            ready.cumulativeSet(index, newState, () -> {
                BluetoothManagerUtils.safeForEachError(governorListeners, listener -> {
                    listener.ready(newState);
                }, logger, "Execution error of a governor listener: ready");
                readyService.completeSilently(CombinedAdapterGovernorImpl.this);
            });
        }

        @Override
        public void lastUpdatedChanged(Instant lastActivity) {
            updateLastInteracted(lastActivity);
        }

    }

}
