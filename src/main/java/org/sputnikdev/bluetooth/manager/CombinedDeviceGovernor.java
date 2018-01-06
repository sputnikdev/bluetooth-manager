package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.URL;

/**
 *
 * @author Vlad Kolotov
 */
public interface CombinedDeviceGovernor extends CombinedGovernor {

    /**
     * Sets connection strategy for the combined device governor.
     * @param strategy a connection strategy
     */
    void setConnectionStrategy(ConnectionStrategy strategy);

    /**
     * Returns the connection strategy of the combined device governor.
     * @return the connection strategy
     */
    ConnectionStrategy getConnectionStrategy();

    /**
     * Sets an adapter URL that is a preferred adapter to be connected to.
     * @param adapter a preferred adapter
     */
    void setPreferredAdapter(URL adapter);

    /**
     * Returns the preferred adapter of the combined device governor.
     * @return the preferred adapter
     */
    URL getPreferredAdapter();

    /**
     * Returns the URL of an adapter the device is connected to. If the device is not connected, then the result is null.
     * @return URL of an adapter the device is connected to
     */
    URL getConnectedAdapter();

}
