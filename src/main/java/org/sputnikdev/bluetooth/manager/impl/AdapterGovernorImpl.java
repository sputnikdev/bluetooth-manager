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
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author Vlad Kolotov
 */
class AdapterGovernorImpl extends AbstractBluetoothObjectGovernor<Adapter> implements AdapterGovernor {

    private Logger logger = LoggerFactory.getLogger(AdapterGovernorImpl.class);

    private final List<AdapterListener> adapterListeners = new CopyOnWriteArrayList<>();

    private PoweredNotification poweredNotification;
    private DiscoveringNotification discoveringNotification;

    private boolean poweredControl = true;
    private boolean discoveringControl = true;
    private double signalPropagationExponent;

    AdapterGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    void init(Adapter adapter) {
        logger.debug("Initializing adapter governor: {}", url);
        enablePoweredNotifications(adapter);
        enableDiscoveringNotifications(adapter);
        logger.trace("Adapter governor initialization performed: {}", url);
    }

    void update(Adapter adapter) {
        logger.debug("Updating adapter governor: {}", url);
        updatePowered(adapter);
        if (adapter.isPowered()) {
            updateDiscovering(adapter);
        }
        updateLastChanged();
        logger.trace("Adapter governor update performed: {}", url);
    }

    @Override
    void reset(Adapter adapter) {
        logger.debug("Resetting adapter governor: {}", url);
        try {
            adapter.disablePoweredNotifications();
            adapter.disableDiscoveringNotifications();
            // force stop discovery and ignore any error
            adapter.stopDiscovery();
        } catch (Exception ex) {
            logger.warn("Error occurred while resetting adapter native object: {} : {}", url, ex.getMessage());
        }
        poweredNotification = null;
        discoveringNotification = null;
        logger.trace("Adapter governor reset performed: {}", url);
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.debug("Disposing adapter governor: {}", url);
        adapterListeners.clear();
        logger.trace("Adapter governor disposed: {}", url);
    }

    @Override
    public boolean getPoweredControl() {
        return poweredControl;
    }

    @Override
    public void setPoweredControl(boolean poweredControl) {
        this.poweredControl = poweredControl;
    }

    @Override
    public boolean isPowered() throws NotReadyException {
        return isReady() && interact("isPowered", Adapter::isPowered);
    }

    @Override
    public boolean getDiscoveringControl() {
        return discoveringControl;
    }

    @Override
    public void setDiscoveringControl(boolean discovering) {
        discoveringControl = discovering;
    }

    @Override
    public boolean isDiscovering() throws NotReadyException {
        return isReady() && interact("isDiscovering", Adapter::isDiscovering);
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {
        interact("setAlias", (Consumer<Adapter>) adapter -> adapter.setAlias(alias));
    }

    @Override
    public String getAlias() throws NotReadyException {
        return interact("getAlias", Adapter::getAlias);
    }

    @Override
    public String getName() throws NotReadyException {
        return interact("getName", Adapter::getName);
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        String alias = getAlias();
        return alias != null ? alias : getName();
    }

    @Override
    public double getSignalPropagationExponent() {
        return signalPropagationExponent;
    }

    @Override
    public void setSignalPropagationExponent(double signalPropagationExponent) {
        this.signalPropagationExponent = signalPropagationExponent;
    }

    @Override
    public List<URL> getDevices() throws NotReadyException {
        return interact("getDevices",
                (Function<Adapter, List<URL>>) adapter -> BluetoothManagerUtils.getURLs(adapter.getDevices()));
    }

    @Override
    public List<DeviceGovernor> getDeviceGovernors() throws NotReadyException {
        return interact("getDeviceGovernors",
                adapter -> (List) bluetoothManager.getGovernors(adapter.getDevices()));
    }

    @Override
    public String toString() {
        String result = "[Adapter] " + getURL();
        if (isReady()) {
            String displayName = getDisplayName();
            if (displayName != null) {
                result += " [" + displayName + "]";
            }
        }
        return result;
    }

    @Override
    public BluetoothObjectType getType() {
        return BluetoothObjectType.ADAPTER;
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

    void notifyPowered(boolean powered) {
        logger.debug("Notifying adapter governor listener (powered): {} : {} : {}",
                url, adapterListeners.size(), powered);
        BluetoothManagerUtils.safeForEachError(adapterListeners,
                listener -> listener.powered(powered), logger,
                "Execution error of a powered listener: " + powered);
    }

    void notifyDiscovering(boolean discovering) {
        logger.debug("Notifying adapter governor listener (discovering): {} : {} : {}",
                url, adapterListeners.size(), discovering);
        BluetoothManagerUtils.safeForEachError(adapterListeners,
                listener -> listener.discovering(discovering), logger,
                "Execution error of a discovering listener: " + discovering);
    }

    private void updatePowered(Adapter adapter) {
        logger.trace("Updating adapter governor powered state: {}", url);
        boolean powered = adapter.isPowered();
        logger.trace("Powered state: {} : {} (control) / {} (state)", url, poweredControl, powered);
        if (poweredControl != powered) {
            logger.debug("Setting powered: {} : {}", url, poweredControl);
            adapter.setPowered(poweredControl);
            if (!adapter.isPowered()) {
                throw new NotReadyException("Could not power adapter");
            }
        }
    }

    private void updateDiscovering(Adapter adapter) {
        logger.trace("Updating adapter governor discovering state: {}", url);
        boolean isDiscovering = adapter.isDiscovering();
        logger.trace("Discovering state: {} : {} (control) / {} (state)", url, discoveringControl, isDiscovering);
        if (discoveringControl && !isDiscovering) {
            logger.debug("Starting discovery: {}", url);
            adapter.startDiscovery();
        } else if (!discoveringControl && isDiscovering) {
            logger.debug("Stopping discovery: {}", url);
            adapter.stopDiscovery();
        }
    }

    private void enablePoweredNotifications(Adapter adapter) {
        logger.debug("Enabling powered notifications: {} : {} ", url, poweredNotification == null);
        if (poweredNotification == null) {
            poweredNotification = new PoweredNotification();
            adapter.enablePoweredNotifications(poweredNotification);
            logger.trace("Powered notifications enabled: {}", url);
        }
    }

    private void enableDiscoveringNotifications(Adapter adapter) {
        logger.debug("Enabling discovering notifications: {} : {}", url, discoveringNotification == null);
        if (discoveringNotification == null) {
            discoveringNotification = new DiscoveringNotification();
            adapter.enableDiscoveringNotifications(discoveringNotification);
            logger.trace("Discovering notifications enabled: {}", url);
        }
    }

    private class PoweredNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean powered) {
            notifyPowered(powered);
            updateLastChanged();
        }
    }

    private class DiscoveringNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean discovering) {
            notifyDiscovering(discovering);
            updateLastChanged();
        }
    }
}
