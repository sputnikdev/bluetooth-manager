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


/**
 * A listener of discovery events.
 *
 * @author Vlad Kolotov
 */
@FunctionalInterface
public interface DeviceDiscoveryListener {

    /**
     * Fires when a new bluetooth adapter or bluetooth device gets discovered.
     *
     * @param discoveredDevice a new discovered bluetooth adapter or bluetooth device
     */
    void discovered(DiscoveredDevice discoveredDevice);

    /**
     * Fires when a bluetooth adapter or a bluetooth device gets lost.
     *
     * @param url of a bluetooth adapter or device
     */
    default void deviceLost(URL url) { }

}
