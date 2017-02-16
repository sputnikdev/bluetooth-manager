package org.sputnikdev.bluetooth.manager;


public interface AdapterDiscoveryListener {

    void discovered(String address);

    void lost(String address);

}
