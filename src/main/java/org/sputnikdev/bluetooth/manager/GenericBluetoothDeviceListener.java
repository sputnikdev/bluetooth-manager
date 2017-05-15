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

/**
 *
 * A listener of events for generic bluetooth devices.
 *
 * @author Vlad Kolotov
 */
public interface GenericBluetoothDeviceListener {

    /**
     * Fires when a device gets online.
     */
    void online();

    /**
     * Fires then a device gets offline.
     */
    void offline();

    /**
     * Fires when a device gets blocked/unblocked.
     * @param blocked is true when device get blocked, false otherwise
     */
    void blocked(boolean blocked);

    /**
     * Reports a device RSSI level.
     * @param rssi a device RSSI level
     */
    void rssiChanged(short rssi);

}
