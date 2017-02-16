package org.sputnikdev.bluetooth.manager;

import org.sputnikdev.bluetooth.gattparser.URL;

public class DiscoveredDevice {
    
    private final URL url;
    private final String name;
    private final String alias;
    private short rssi;
    private int bluetoothClass;

    public DiscoveredDevice(URL url, String name, String alias) {
        this.url = url;
        this.name = name;
        this.alias = alias;
    }

    public DiscoveredDevice(URL url, String name, String alias, short rssi, int bluetoothClass) {
        this.url = url;
        this.name = name;
        this.alias = alias;
        this.rssi = rssi;
        this.bluetoothClass = bluetoothClass;
    }

    public URL getURL() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public short getRSSI() {
        return rssi;
    }

    public int getBluetoothClass() {
        return bluetoothClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DiscoveredDevice that = (DiscoveredDevice) o;
        return url.equals(that.url);

    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }
}
