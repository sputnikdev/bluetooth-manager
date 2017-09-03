package org.sputnikdev.bluetooth.manager.transport;

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
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;

import java.util.List;

/**
 * A root interface for all Bluetooth transport implementations.
 *
 * @author Vlad Kolotov
 */
public interface BluetoothObjectFactory {


    /**
     * Returns an adapter by its URl. URL may not contain 'protocol' part.
     * @param url adapter URL
     * @return an adapter
     */
    Adapter getAdapter(URL url);

    /**
     * Returns a device by its URl. URL may not contain 'protocol' part.
     * @param url device URL
     * @return a device
     */
    Device getDevice(URL url);

    /**
     * Returns a characteristic by its URl. URL may not contain 'protocol' part.
     * @param url characteristic URL
     * @return a characteristic
     */
    Characteristic getCharacteristic(URL url);

    /**
     * Returns all discovered adapters by all registered transports.
     * @return all discovered adapters
     */
    List<DiscoveredAdapter> getDiscoveredAdapters();

    /**
     * Returns all discovered devices by all registered transports.
     * @return all discovered devices
     */
    List<DiscoveredDevice> getDiscoveredDevices();

    /**
     * Returns transport protocol name
     * @return transport protocol name
     */
    String getProtocolName();

}
