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

import java.util.Collections;
import java.util.List;

import org.sputnikdev.bluetooth.URL;

/**
 * A class to capture discovered GATT services.
 *
 * @author Vlad Kolotov
 */
public class GattService {

    private final URL uuid;
    private final List<GattCharacteristic> characteristics;

    /**
     * Create a new object.
     * @param uuid service URL
     * @param characteristics a list of service characteristics
     */
    public GattService(URL uuid, List<GattCharacteristic> characteristics) {
        this.uuid = uuid;
        this.characteristics = Collections.unmodifiableList(characteristics);
    }

    /**
     * Returns service URL.
     * @return serice URL
     */
    public URL getURL() {
        return uuid;
    }

    /**
     * Returns characteristics list of the service.
     * @return characteristics list of the service
     */
    public List<GattCharacteristic> getCharacteristics() {
        return characteristics;
    }

}
