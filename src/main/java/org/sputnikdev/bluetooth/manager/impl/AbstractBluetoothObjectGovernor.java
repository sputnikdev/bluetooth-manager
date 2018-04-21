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
import org.sputnikdev.bluetooth.manager.BluetoothFatalException;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothInteractionException;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.GovernorState;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A root class for all governors in the system. Defines lifecycle and error handling/recovery processes for governors.
 *
 * <p>The lifecycle is divided on the following stages:
 *
 * <p><ul>
 *     <li>Native object acquisition</li>
 *     <li>Error handling and recovery</li>
 *     <li>Governing/maintaining state of the bluetooth object</li>
 * </ul>
 *
 * <p>All the stages above are handled in {@link #update()} method. When invoked, this method tries to acquire
 * a native object for corresponding device first. When acquired, {@link #init(BluetoothObject)} method is called
 * to perform initialisation (setting initial state, subscribing to the object events), then all registered governor
 * listeners are notified that the governor is "ready" by triggering {@link GovernorListener#ready(boolean)} method.
 * Once initialised, the {@link #update()} method switches to the next stage - maintaining state of the bluetooth
 * object by invoking {@link #update(BluetoothObject)} method, this is where main manipulations with
 * the bluetooth object are happening. If any exception occurs in the {@link #update(BluetoothObject)} method,
 * the Error handling stage begins by triggering {@link #reset(BluetoothObject)} method, which must revert
 * internal state of the governor to the initial state - the Native object acquisition.
 *
 * <p>In short this looks like that:
 *
 * <p>The update method is called outside of the governor (by a separate thread):
 * governor.update();
 *
 * <p>then the following happens:
 *
 * {@link #init(BluetoothObject)}
 * {@link GovernorListener#ready(boolean)} with argument - true
 * {@link #update(BluetoothObject)}
 *
 * <p>if the {@link #update(BluetoothObject)} method throws any exception, then
 * {@link #reset(BluetoothObject)}
 * {@link GovernorListener#ready(boolean)} with argument - false
 * are invoked, which brings the governor to its initial state, where everything begins from the start.
 *
 * <p>In order to help to release resources (native objects) {@link #reset(BluetoothObject)} method is used,
 * which must release any acquired resources, disconnect and unsubscribe from notifications.
 * It is recommended to call this method when, for example, a program exists or in any similar cases.
 *
 * @author Vlad Kolotov
 */
abstract class AbstractBluetoothObjectGovernor<T extends BluetoothObject> implements BluetoothObjectGovernor {

    private Logger logger = LoggerFactory.getLogger(AbstractBluetoothObjectGovernor.class);

    protected final BluetoothManagerImpl bluetoothManager;
    protected final URL url;
    private T bluetoothObject;
    private String transport;
    private Instant lastInteracted;
    private Instant lastChangedNotified;
    private Instant ready;
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();
    private GovernorState state = GovernorState.NEW;
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final CompletableFutureService<AbstractBluetoothObjectGovernor> futureService =
            new CompletableFutureService<>();

    AbstractBluetoothObjectGovernor(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isReady() {
        return bluetoothObject != null;
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
    public Instant getLastInteracted() {
        return lastInteracted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractBluetoothObjectGovernor)) {
            return false;
        }
        AbstractBluetoothObjectGovernor<?> that = (AbstractBluetoothObjectGovernor<?>) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    public String getTransport() {
        return transport;
    }

    @Override
    public void init() {
        update();
    }

    @Override
    public void update() {
        if (state != GovernorState.DISPOSED) {
            logger.trace("Updating governor: {}", url);
            boolean updated = false;
            T object = null;
            try {
                logger.trace("Lock acquired. Getting a native object: {}", url);
                object = getOrFindBluetoothObject();
                if (object == null) {
                    logger.trace("Native object is not available: {}", url);
                    return;
                }
                logger.trace("Performing update with the native object: {} / {}",
                        url, Integer.toHexString(object.hashCode()));
                update(object);
                logger.trace("Governor has been updated: {}", url);
                updated = true;
                if (state != GovernorState.READY) {
                    state = GovernorState.READY;
                    notifyReady(true);
                }
                // handling completable futures
                futureService.complete(this);
            } catch (BluetoothFatalException fatal) {
                logger.warn("A fatal error occurred while updating governor, a higher level governor "
                        + "must be forced to reset: {} : {}", url, fatal.getMessage());
                reset();
            } catch (Exception ex) {
                logger.warn("Error occurred while updating governor: {} / {} : {}",
                        url, object != null ? Integer.toHexString(object.hashCode()) : null, ex.getMessage());
                reset();
            }
            if (updated) {
                notifyLastChanged();
            }
        }
    }

    public void reset() {
        if (state != GovernorState.RESET && state != GovernorState.DISPOSED) {
            state = GovernorState.RESET;
            if (!url.isCharacteristic()) {
                logger.debug("Resetting governor. Descendants first: {}", url);
                bluetoothManager.resetDescendants(url);
            }
            try {
                if (bluetoothObject != null) {
                    forceReset(bluetoothObject);
                }
                bluetoothObject = null;
                logger.debug("Governor has been reset: {}", url);
            } catch (Exception ex) {
                logger.debug("Error occurred while resetting governor {}: {}", url, ex.getMessage());
            }
        }
    }

    @Override
    public void dispose() {
        if (state != GovernorState.DISPOSED) {
            logger.warn("Disposing governor: {}", url);
            reset();
            state = GovernorState.DISPOSED;
            governorListeners.clear();
            futureService.clear();
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <G extends BluetoothGovernor, V> CompletableFuture<V> when(Predicate<G> predicate, Function<G, V> function) {
        return futureService.submit(this, (Predicate<AbstractBluetoothObjectGovernor>) predicate,
                (Function<AbstractBluetoothObjectGovernor, V>) function);
    }

    protected void scheduleUpdate() {
        bluetoothManager.scheduleUpdate(this);
    }

    protected <R> R interact(String name, Function<T, R> delegate) {
        return interact(name, delegate, false);
    }

    protected <R> R interact(String name, Function<T, R> delegate, boolean update) {
        try {
            T object = getBluetoothObject();
            logger.trace("Interacting with native object ({}): {} / {}",
                    name, url, Integer.toHexString(object.hashCode()));
            R result = delegate.apply(object);
            logger.trace("Interaction completed ({}): {} / {}", name, url, Integer.toHexString(object.hashCode()));
            if (update) {
                updateLastInteracted();
            }
            return result;
        } catch (Exception ex) {
            String message = String.format("Error occurred while interacting (%s) with native object: %s : %s",
                    name, url, ex.getMessage());
            logger.warn(message);
            reset();
            throw new BluetoothInteractionException(message, ex);
        }
    }

    protected <V> void interact(String name, BiConsumer<T, V> delegate, V value) {
        interact(name, (Function<T, V>) object -> {
            delegate.accept(object, value);
            return null;
        }, true);
    }

    protected void interact(String name, Consumer<T> delegate) {
        interact(name, (Function<T, Void>) object -> {
            delegate.accept(object);
            return null;
        });
    }

    private T getBluetoothObject() throws NotReadyException {
        if (bluetoothObject == null) {
            throw new NotReadyException("Bluetooth object is not ready: " + url);
        }
        return bluetoothObject;
    }

    abstract void init(T object);

    abstract void update(T object);

    abstract void reset(T object);

    void updateLastInteracted() {
        lastInteracted = Instant.now();
    }

    void notifyReady(boolean ready) {
        bluetoothManager.notify(() -> {
            BluetoothManagerUtils.forEachSilently(governorListeners, GovernorListener::ready, ready, logger,
                    "Execution error of a governor listener: ready");
            bluetoothManager.notifyGovernorReady(this, ready);
        });
    }

    void notifyLastChanged() {
        notifyLastChanged(lastInteracted);
    }

    void notifyLastChanged(Instant time) {
        if (time != null && !time.equals(lastChangedNotified)) {
            bluetoothManager.notify(governorListeners, GovernorListener::lastUpdatedChanged, time, logger,
                    "Execution error of a governor listener: last changed");
            lastChangedNotified = time;
        }
    }

    Instant getReady() {
        return ready;
    }

    private T getOrFindBluetoothObject() {
        logger.trace("Acquiring native object: {}", url);
        if (bluetoothObject == null) {
            logger.trace("Native object is null. Trying to get a new native object from manager: {}", url);
            bluetoothObject = bluetoothManager.getBluetoothObject(
                    transport != null ? url.copyWithProtocol(transport) : url);
            if (bluetoothObject != null) {
                ready = Instant.now();
                logger.debug("A new native object has been acquired: {}", url);
                // update internal cache so that next time acquiring "native" object will be faster
                transport = bluetoothObject.getURL().getProtocol();
                try {
                    logger.debug("Initializing governor with the new native object: {}", url);
                    init(bluetoothObject);
                    logger.trace("Initialization succeeded: {}", url);
                } catch (Exception ex) {
                    logger.warn("Error occurred while initializing governor with a new native object: {} : {}",
                            url, ex.getMessage());
                    throw ex;
                }
            }
        }
        logger.trace("Returning native object: {}", url);
        return bluetoothObject;
    }

    private void forceReset(T bluetoothObject) {
        try {
            logger.trace("Resetting native object: {} / {}", url, Integer.toHexString(bluetoothObject.hashCode()));
            reset(bluetoothObject);
        } catch (Exception ex) {
            logger.trace("Could not reset bluetooth object {}: {}", url, ex.getMessage());
        }
        notifyReady(false);
        try {
            logger.trace("Disposing native object: {} / {}", url, Integer.toHexString(bluetoothObject.hashCode()));
            bluetoothManager.disposeBluetoothObject(bluetoothObject.getURL());
        } catch (Exception ex) {
            logger.trace("Could not dispose bluetooth object {}: {}", url, ex.getMessage());
        }
    }

}
