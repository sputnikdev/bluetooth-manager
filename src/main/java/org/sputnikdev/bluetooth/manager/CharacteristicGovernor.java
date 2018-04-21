package org.sputnikdev.bluetooth.manager;

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

import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bluetooth characteristic governor ({@link BluetoothGovernor}).
 *
 * @author Vlad Kolotov
 */
public interface CharacteristicGovernor extends BluetoothGovernor {

    /**
     * Returns access types (flags) supported by the characteristic.
     *
     * @return flags supported by the characteristic
     * @throws NotReadyException if the bluetooth object is not ready
     */
    Set<CharacteristicAccessType> getFlags() throws NotReadyException;

    /**
     * Checks whether the characteristic can be read.
     *
     * @return true if the characteristic can be read, otherwise false
     * @throws NotReadyException if the bluetooth object is not ready
     */
    boolean isReadable() throws NotReadyException;

    /**
     * Checks whether the characteristic can be written.
     *
     * @return true if the characteristic can be written, otherwise false
     * @throws NotReadyException if the bluetooth object is not ready
     */
    boolean isWritable() throws NotReadyException;

    /**
     * Checks whether the characteristic can notify.
     *
     * @return true if the characteristic can notify, otherwise false
     * @throws NotReadyException if the bluetooth object is not ready
     */
    boolean isNotifiable() throws NotReadyException;


    /**
     * Return true if notification is enabled, false otherwise.
     * @return true if notification is enabled, false otherwise
     * @throws NotReadyException if the bluetooth object is not ready
     */
    boolean isNotifying() throws NotReadyException;

    /**
     * Reads state from the characteristic.
     *
     * @return characteristic state
     * @throws NotReadyException if the bluetooth object is not ready
     */
    byte[] read() throws NotReadyException;

    /**
     * Writes state to the characteristic.
     * @param data a new characteristic state
     * @return true if the new state is written
     * @throws NotReadyException if the bluetooth object is not ready
     */
    boolean write(byte[] data) throws NotReadyException;

    /**
     * Register a new characteristic listener.
     * @param valueListener new characteristic listener
     */
    void addValueListener(ValueListener valueListener);

    /**
     * Removes a previously registered characteristic listener.
     * @param valueListener a previously registered characteristic listener
     */
    void removeValueListener(ValueListener valueListener);

    /**
     * Returns the date/time of last known received notification.
     * @return the date/time of last known received notification
     */
    Instant getLastNotified();

    boolean isAuthenticated();


    default <G extends CharacteristicGovernor, V> CompletableFuture<V> whenAuthenticated(Function<G, V> function) {
        return when(CharacteristicGovernor::isAuthenticated, function);
    }

    default <G extends CharacteristicGovernor> CompletableFuture<Void> whenAuthenticatedThanDo(Consumer<G> consumer) {
        return whenAuthenticated(g -> {
            consumer.accept((G) this);
            return null;
        });
    }

}
