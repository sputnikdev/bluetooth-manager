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

import java.util.Date;

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;

/**
 * An interface for all Bluetooth governors. Bluetooth governors are the central part of the system. They represent
 * different Bluetooth objects such us adapters ({@link Adapter}),
 * devices ({@link Device}) and characteristics
 * ({@link Characteristic}). By its nature, Bluetooth protocol
 * and Bluetooth communication is unstable (devices and adapters can get disconnected or can be out of radio range).
 * Therefore the main function of the bluetooth governors is to provide robustness to Bluetooth protocol
 * and communication, e.g. once a bluetooth governor is created, it is monitoring and recovering the state of
 * a corresponding bluetooth object. Once the state of a corresponding bluetooth object is recovered,
 * bluetooth governor changes its status to "ready" ({@link BluetoothGovernor#isReady()}) and fires
 * {@link GovernorListener#ready(boolean)} listener.
 * <br/>Bluetooth governors provide access to some attributes/properties of their corresponding Bluetooth objects
 * through their getter and setter methods. There are two types of getter and setter methods:
 * <ul>
 * <li>Online methods. These are methods that provide direct access to the bluetooth objects. They expect that
 * the corresponding bluetooth object is acquired (ready for use); if the bluetooth object is not ready, then they
 * can throw {@link NotReadyException} exception.</li>
 * <li>Offline methods. These are methods that set the bluetooth object state which is to be monitored/recovered by
 * the Bluetooth Manager. For example, {@link AdapterGovernor#getDiscoveringControl()}
 * and {@link AdapterGovernor#setDiscoveringControl(boolean)} are "offline" methods, by setting it to true,
 * the Bluetooth Manager will insure that the corresponding adapter is always in "discovering" state.
 * The naming convention for this type of methods is setXxxControl and getXxxControl.</li>
 * </ul>
 * <br/>Normally an attribute of a bluetooth object would have three methods:
 * <ul>
 * <li>an online method (direct access method) to get a status/value of the attribute</li>
 * <li>an offline method to set "control state" of the attribute which will be automatically controlled
 * (kept in its state) by the Bluetooth Manager</li>
 * <li>an offline method to get "control state" of the attribute</li>
 * </ul>
 * <br/>See also org.sputnikdev.bluetooth.manager.impl.BluetoothObjectGovernor for more info about
 * internal implementation
 *
 * @author Vlad Kolotov
 */
public interface BluetoothGovernor {

    /**
     * Returns the URL of the corresponding Bluetooth object.
     * @return the URL of the corresponding Bluetooth object
     */
    URL getURL();

    /**
     * Checks whether the governor is in state when its corresponding bluetooth object is acquired
     * and ready for manipulations.
     * @return true if the corresponding bluetooth object is acquired and ready for manipulations
     */
    boolean isReady();

    /**
     * Returns type of the corresponding Bluetooth object.
     * @return type of the corresponding Bluetooth object
     */
    BluetoothObjectType getType();

    /**
     * Returns the last known date of the bluetooth object activity.
     * @return the last known date of the bluetooth object activity
     */
    Date getLastActivity();

    /**
     * An accept method of the visitor pattern to process different bluetooth governors at once.
     * @param visitor bluetooth governor visitor
     * @throws Exception in case of any error
     */
    void accept(BluetoothObjectVisitor visitor) throws Exception;

    /**
     * Register a new governor listener.
     * @param listener a new governor listener
     */
    void addGovernorListener(GovernorListener listener);

    /**
     * Unregister a governor listener.
     * @param listener a governor listener
     */
    void removeGovernorListener(GovernorListener listener);

}
