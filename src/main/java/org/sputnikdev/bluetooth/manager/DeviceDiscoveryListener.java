package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.gattparser.URL;

public interface DeviceDiscoveryListener {

    void discovered(DiscoveredDevice discoveredDevice);

    void lost(URL url);

}
