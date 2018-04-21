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
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.ManagerListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.ValueListener;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @author Vlad Kolotov
 */
class CombinedCharacteristicGovernorImpl
        implements CharacteristicGovernor, BluetoothObjectGovernor, CombinedGovernor {

    private Logger logger = LoggerFactory.getLogger(CombinedCharacteristicGovernorImpl.class);

    private BluetoothManagerImpl bluetoothManager;
    private final URL url;

    private final ManagerListener delegateListener = new DelegatesListener();

    private CharacteristicGovernor delegate;
    private final List<ValueListener> valueListeners = new CopyOnWriteArrayList<>();
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private final CompletableFutureService<CharacteristicGovernor> futureService = new CompletableFutureService<>();
    private Instant lastInteracted;
    private Instant lastNotified;


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
        synchronized (delegateListener) {
            valueListeners.add(valueListener);
            if (delegate != null) {
                delegate.addValueListener(valueListener);
            }
        }
    }

    @Override
    public void removeValueListener(ValueListener valueListener) {
        synchronized (delegateListener) {
            valueListeners.remove(valueListener);
            if (delegate != null) {
                delegate.removeValueListener(valueListener);
            }
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
    public Instant getLastInteracted() {
        return delegate != null ? delegate.getLastInteracted() : lastInteracted;
    }

    @Override
    public Instant getLastNotified() {
        return delegate != null ? delegate.getLastNotified() : lastNotified;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        if (delegate != null) {
            visitor.visit(delegate);
        }
    }

    @Override
    public void addGovernorListener(GovernorListener listener) {
        synchronized (delegateListener) {
            governorListeners.add(listener);
            if (delegate != null) {
                delegate.addGovernorListener(listener);
            }
        }
    }

    @Override
    public void removeGovernorListener(GovernorListener listener) {
        synchronized (delegateListener) {
            governorListeners.remove(listener);
            if (delegate != null) {
                delegate.removeGovernorListener(listener);
            }
        }
    }

    @Override
    public void init() {
        logger.debug("Initializing combined characteristic governor: {}", url);
        bluetoothManager.addManagerListener(delegateListener);
        update();
        logger.debug("Combined characteristic governor initialization completed: {}", url);
    }

    @Override
    public void update() {
        logger.debug("Updating combined characteristic governor: {}", url);
        if (delegate == null) {
            bluetoothManager.getDiscoveredAdapters().stream()
                    .filter(CombinedCharacteristicGovernorImpl::notCombined)
                    .map(this::getDelegate)
                    .filter(BluetoothGovernor::isReady)
                    .findFirst()
                    .ifPresent(this::installDelegate);
        }
        futureService.completeSilently(this);
        logger.debug("Combined characteristic governor update completed: {}", url);
    }

    @Override
    public void reset() {
        if (delegate != null) {
            uninstallDelegate(delegate.getURL());
        }
    }

    @Override
    public void dispose() {
        bluetoothManager.removeManagerListener(delegateListener);
        reset();
        governorListeners.clear();
        valueListeners.clear();
        futureService.clear();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <G extends BluetoothGovernor, V> CompletableFuture<V> when(Predicate<G> predicate, Function<G, V> function) {
        return futureService.submit(this, (Predicate<CharacteristicGovernor>) predicate,
                (Function<CharacteristicGovernor, V>) function);
    }

    @Override
    public boolean isAuthenticated() {
        return delegate != null && delegate.isAuthenticated();
    }

    private void installDelegate(CharacteristicGovernor delegate) {
        synchronized (delegateListener) {
            if (this.delegate == null) {
                logger.debug("Installing delegate: {}", delegate.getURL());
                this.delegate = delegate;
                governorListeners.forEach(delegate::addGovernorListener);
                valueListeners.forEach(delegate::addValueListener);
                lastInteracted = delegate.getLastInteracted();
                lastNotified = delegate.getLastNotified();
            } else if (!this.delegate.equals(delegate)) {
                throw new IllegalStateException("Delegate un-ready event has been missed: " + url);
            } else {
                logger.debug("Skipping delegate as it has been installed already: " + url);
            }
        }
        bluetoothManager.notify(() -> {
            if (delegate.isReady()) {
                BluetoothManagerUtils.forEachSilently(governorListeners, GovernorListener::ready, true, logger,
                        "Execution error of a governor listener: ready");
            }
            BluetoothManagerUtils.forEachSilently(governorListeners, GovernorListener::lastUpdatedChanged,
                    lastInteracted, logger,"Execution error of a governor listener: lastUpdatedChanged");
        });
    }

    private void uninstallDelegate(URL delegateURL) {
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null && delegate.getURL().equals(delegateURL)) {
            synchronized (delegateListener) {
                governorListeners.forEach(delegate::removeGovernorListener);
                valueListeners.forEach(delegate::removeValueListener);
                lastInteracted = delegate.getLastInteracted();
                lastNotified = delegate.getLastNotified();
                this.delegate = null;
            }
        }
    }

    private CharacteristicGovernor getDelegate() {
        CharacteristicGovernor delegate = this.delegate;
        if (delegate != null) {
            return delegate;
        }
        throw new NotReadyException("Combined characteristic governor is not ready yet");
    }

    private class DelegatesListener implements ManagerListener {
        @Override
        public void ready(BluetoothGovernor governor, boolean isReady) {
            if (governor instanceof CharacteristicGovernor
                    && governor.getURL().copyWithProtocol(null).copyWithAdapter(COMBINED_ADDRESS).equals(url)) {
                if (isReady) {
                    installDelegate((CharacteristicGovernor) governor);
                } else {
                    uninstallDelegate(governor.getURL());
                }
            }
        }
    }

    private static boolean notCombined(DiscoveredAdapter adapter) {
        return !COMBINED_ADDRESS.equals(adapter.getURL().getAdapterAddress());
    }

    private CharacteristicGovernor getDelegate(DiscoveredAdapter adapter) {
        return bluetoothManager.getCharacteristicGovernor(url.copyWithAdapter(adapter.getURL().getAdapterAddress()));
    }

}
