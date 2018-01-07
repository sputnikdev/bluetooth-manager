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
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.ManagerListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.ValueListener;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author Vlad Kolotov
 */
class CombinedCharacteristicGovernorImpl
        implements CharacteristicGovernor, BluetoothObjectGovernor, CombinedGovernor {

    private Logger logger = LoggerFactory.getLogger(CombinedCharacteristicGovernorImpl.class);

    private BluetoothManagerImpl bluetoothManager;
    private final URL url;
    private CharacteristicGovernor delegate;
    private final List<ValueListener> valueListeners = new CopyOnWriteArrayList<>();
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private Date lastActivity;
    private final ManagerListener delegateListener = new DelegatesListener();

    CombinedCharacteristicGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    @Override
    public Set<CharacteristicAccessType> getFlags() throws NotReadyException {
        return getDelegate().getFlags();
    }

    @Override
    public boolean isReadable() throws NotReadyException {
        return getDelegate().isReadable();
    }

    @Override
    public boolean isWritable() throws NotReadyException {
        return getDelegate().isWritable();
    }

    @Override
    public boolean isNotifiable() throws NotReadyException {
        return getDelegate().isNotifiable();
    }

    @Override
    public boolean isNotifying() throws NotReadyException {
        return getDelegate().isNotifying();
    }

    @Override
    public byte[] read() throws NotReadyException {
        return getDelegate().read();
    }

    @Override
    public boolean write(byte[] data) throws NotReadyException {
        return getDelegate().write(data);
    }

    @Override
    public void addValueListener(ValueListener valueListener) {
        valueListeners.add(valueListener);
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            delegate.addValueListener(valueListener);
        }
    }

    @Override
    public void removeValueListener(ValueListener valueListener) {
        valueListeners.remove(valueListener);
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            delegate.removeValueListener(valueListener);
        }
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isReady() {
        return delegate != null && delegate.isReady();
    }

    @Override
    public BluetoothObjectType getType() {
        return BluetoothObjectType.CHARACTERISTIC;
    }

    @Override
    public Date getLastActivity() {
        return delegate != null ? delegate.getLastActivity() : lastActivity;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        if (delegate != null) {
            visitor.visit(delegate);
        }
    }

    @Override
    public void addGovernorListener(GovernorListener listener) {
        governorListeners.add(listener);
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            delegate.addGovernorListener(listener);
        }
    }

    @Override
    public void removeGovernorListener(GovernorListener listener) {
        governorListeners.remove(listener);
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            delegate.removeGovernorListener(listener);
        }
    }

    @Override
    public void init() {
        bluetoothManager.addManagerListener(delegateListener);

        bluetoothManager.getRegisteredGovernors().stream()
                .filter(registeredURL -> !COMBINED_ADDRESS.equals(registeredURL.getAdapterAddress())
                        && registeredURL.copyWithProtocol(null).copyWithAdapter(COMBINED_ADDRESS).equals(url))
                .map(registeredURL -> bluetoothManager.getGovernor(registeredURL))
                .filter(BluetoothGovernor::isReady)
                .reduce((a, b) -> { throw new IllegalStateException("multiple 'ready' characteristics found"); })
                .ifPresent(governor -> installDelegate((CharacteristicGovernor) governor));
    }

    @Override
    public void update() {
        if (delegate == null) {
            // try to find and register delegate
            bluetoothManager.getRegisteredGovernors().stream()
                    .filter(this::targetDevice)
                    .forEach(this::registerTargetCharacteristic);
            // do nothing else, bluetooth manager takes care of installing the delegate through the ManagerListener
        }
    }

    @Override
    public void reset() {
        uninstallDelegate();
    }

    @Override
    public void dispose() {
        bluetoothManager.removeManagerListener(delegateListener);
        reset();
        governorListeners.clear();
        valueListeners.clear();
    }

    private void installDelegate(CharacteristicGovernor delegate) {
        synchronized (delegateListener) {
            this.delegate = delegate;
            governorListeners.forEach(delegate::addGovernorListener);
            valueListeners.forEach(delegate::addValueListener);
            lastActivity = delegate.getLastActivity();
        }
        if (delegate.isReady()) {
            BluetoothManagerUtils.safeForEachError(governorListeners, listener -> listener.ready(true), logger,
                    "Execution error of a governor listener: ready");
        }
        BluetoothManagerUtils.safeForEachError(governorListeners,
                listener -> listener.lastUpdatedChanged(lastActivity),
                logger,"Execution error of a governor listener: lastUpdatedChanged");
    }

    private void uninstallDelegate() {
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            synchronized (delegateListener) {
                governorListeners.forEach(delegate::removeGovernorListener);
                valueListeners.forEach(delegate::removeValueListener);
                lastActivity = delegate.getLastActivity();
            }
        }
        this.delegate = null;
    }

    private CharacteristicGovernor getDelegate() {
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            return delegate;
        }
        throw new NotReadyException("Combined characteristic governor is not ready yet");
    }

    private void registerTargetCharacteristic(URL deviceURL) {
        bluetoothManager.getCharacteristicGovernor(url.copyWithAdapter(deviceURL.getAdapterAddress()));
    }

    private boolean targetDevice(URL registeredURL) {
        return registeredURL.isDevice()
                && !COMBINED_ADDRESS.equalsIgnoreCase(registeredURL.getAdapterAddress())
                && registeredURL.getDeviceAddress().equalsIgnoreCase(url.getDeviceAddress())
                && ((DeviceGovernor) bluetoothManager.getGovernor(registeredURL)).isServicesResolved();
    }

    private class DelegatesListener implements ManagerListener {
        @Override
        public void ready(BluetoothGovernor governor, boolean isReady) {
            if (governor instanceof CharacteristicGovernorImpl
                    && governor.getURL().copyWithProtocol(null).copyWithAdapter(COMBINED_ADDRESS).equals(url)) {
                if (isReady) {
                    installDelegate((CharacteristicGovernor) governor);
                } else {
                    uninstallDelegate();
                }
            }
        }
    }
}
