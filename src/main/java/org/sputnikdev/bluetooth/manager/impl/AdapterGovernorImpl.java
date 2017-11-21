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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Vlad Kolotov
 */
class AdapterGovernorImpl extends BluetoothObjectGovernor<Adapter> implements AdapterGovernor {

    private Logger logger = LoggerFactory.getLogger(AdapterGovernorImpl.class);

    private final List<AdapterListener> adapterListeners = new ArrayList<>();

    private PoweredNotification poweredNotification;
    private DiscoveringNotification discoveringNotification;

    private boolean poweredControl = true;
    private boolean discoveringControl = true;

    AdapterGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    void init(Adapter adapter) {
        enablePoweredNotifications(adapter);
        enableDiscoveringNotifications(adapter);
    }

    void update(Adapter adapter) {
        updatePowered(adapter);
        if (isPowered()) {
            updateDiscovering(adapter);
        }
    }

    @Override
    void reset(Adapter adapter) {
        adapter.disablePoweredNotifications();
        adapter.disableDiscoveringNotifications();
        poweredNotification = null;
        discoveringNotification = null;
        if (isPowered() && adapter.isDiscovering()) {
            adapter.stopDiscovery();
        }
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
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isPowered();
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
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isDiscovering();
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {
        getBluetoothObject().setAlias(alias);
    }

    @Override
    public String getAlias() throws NotReadyException {
        return getBluetoothObject().getAlias();
    }

    @Override
    public String getName() throws NotReadyException {
        return getBluetoothObject().getName();
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        String alias = getAlias();
        return alias != null ? alias : getName();
    }

    @Override
    public List<URL> getDevices() throws NotReadyException {
        return BluetoothManagerUtils.getURLs(getBluetoothObject().getDevices());
    }

    @Override
    public List<DeviceGovernor> getDeviceGovernors() throws NotReadyException {
        return (List) bluetoothManager.getGovernors(getBluetoothObject().getDevices());
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
        synchronized (adapterListeners) {
            adapterListeners.add(adapterListener);
        }
    }

    @Override
    public void removeAdapterListener(AdapterListener adapterListener) {
        synchronized (adapterListeners) {
            adapterListeners.remove(adapterListener);
        }
    }

    void notifyPowered(boolean powered) {
        synchronized (adapterListeners) {
            for (AdapterListener listener : adapterListeners) {
                try {
                    listener.powered(powered);
                } catch (Exception ex) {
                    logger.error("Execution error of a powered listener: " + powered, ex);
                }
            }
        }
    }

    void notifyDiscovering(boolean discovering) {
        synchronized (adapterListeners) {
            for (AdapterListener listener : adapterListeners) {
                try {
                    listener.discovering(discovering);
                } catch (Exception ex) {
                    logger.error("Execution error of a discovering listener: " + discovering, ex);
                }
            }
        }
    }

    private void updatePowered(Adapter adapter) {
        if (poweredControl != adapter.isPowered()) {
            adapter.setPowered(poweredControl);
        }
    }

    private void updateDiscovering(Adapter adapter) {
        boolean isDiscovering = adapter.isDiscovering();
        if (discoveringControl && !isDiscovering) {
            adapter.startDiscovery();
        } else if (!discoveringControl && isDiscovering) {
            adapter.stopDiscovery();
        }
    }

    private void enablePoweredNotifications(Adapter adapter) {
        if (poweredNotification == null) {
            poweredNotification = new PoweredNotification();
            adapter.enablePoweredNotifications(poweredNotification);
        }
    }

    private void enableDiscoveringNotifications(Adapter adapter) {
        if (discoveringNotification == null) {
            discoveringNotification = new DiscoveringNotification();
            adapter.enableDiscoveringNotifications(discoveringNotification);
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
