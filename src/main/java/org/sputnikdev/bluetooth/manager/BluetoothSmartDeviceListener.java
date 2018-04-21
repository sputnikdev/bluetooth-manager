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

import org.sputnikdev.bluetooth.URL;

import java.util.List;
import java.util.Map;


/**
 * A listener of events for BLE devices.
 *
 * @author Vlad Kolotov
 */
@FunctionalInterface
public interface BluetoothSmartDeviceListener {

    /**
     * Fires when the device gets connected.
     */
    default void connected() { }

    /**
     * Fires when the device gets disconnected.
     */
    default void disconnected() { }

    /**
     * Fires when GATT services get resolved.
     *
     * @param gattServices a list of resolved GATT services
     */
    void servicesResolved(List<GattService> gattServices);

    /**
     * Fires when GATT services get unresolved.
     */
    default void servicesUnresolved() { }

    /**
     * Fires when the device advertises service data. The key is service UUID (16, 32 or 128 bit),
     * the value is advertised data.
     * @param serviceData service data
     */
    default void serviceDataChanged(Map<URL, byte[]> serviceData) { }

    /**
     * Fires when the device advertises manufacturer data. The key is manufacturer ID, the value is advertised data.
     * @param manufacturerData manufacturer data
     */
    default void manufacturerDataChanged(Map<Short, byte[]> manufacturerData) { }

    default void authenticated() { }

    default void authenticationFailure(Exception reason) { }

}
