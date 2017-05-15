package org.sputnikdev.bluetooth.manager;

import java.util.Date;

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
 * A listener to watch governors events.
 *
 */
public interface GovernorListener {

    /**
     * Reports when a device/governor changes its status. See {@link BluetoothGovernor} for more info.
     * @param isReady true if a device/adapter becomes ready for interactions (hardware acquired), false otherwise
     */
    void ready(boolean isReady);

    /**
     * Reports when a device/governor was last active (receiving events, sending commands etc).
     * @param lastActivity a date when a device was last active
     */
    void lastUpdatedChanged(Date lastActivity);

}
