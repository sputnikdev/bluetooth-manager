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
 * A class to capture discovered GATT characteristics.
 *
 * @author Vlad Kolotov
 */
public class GattCharacteristic {

    private final String uuid;
    private final String[] flags;

    /**
     * Creates a new object.
     * @param uuid characteristic UUID
     * @param flags characteristic access flags
     */
    public GattCharacteristic(String uuid, String[] flags) {
        this.uuid = uuid;
        this.flags = flags;
    }

    /**
     * Returns characteristic UUID.
     * @return characteristic UUID
     */
    public String getUUID() {
        return uuid;
    }

    /**
     * Returns characteristic access flags.
     * @return characteristic access flags
     */
    public String[] getFlags() {
        return flags;
    }
}
