package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.URL;

/**
 * Root interface for all bluetooth objects discoveed by Bluetooth Manager
 */
public interface DiscoveredObject {

    /**
     * Returns bluetooth object URL
     * @return bluetooth object URL
     */
    URL getURL();

    /**
     * Returns bluetooth object name
     * @return bluetooth object name
     */
    String getName();

    /**
     * Returns bluetooth object alias
     * @return bluetooth object alias
     */
    String getAlias();

}
