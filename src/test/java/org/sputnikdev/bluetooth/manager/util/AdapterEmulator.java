package org.sputnikdev.bluetooth.manager.util;

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class AdapterEmulator {

    private Adapter adapter;
    private Map<URL, DeviceEmulator> devices = new HashMap<>();

    public AdapterEmulator(URL url) {
        this(url, url.getAdapterAddress());
    }

    public AdapterEmulator(URL url, String name) {
        adapter = mock(Adapter.class);
        when(adapter.getURL()).thenReturn(url);
        when(adapter.getName()).thenReturn(name);
        when(adapter.getDevices()).thenAnswer(answer -> devices.values().stream()
                .map(DeviceEmulator::getDevice).collect(Collectors.toList()));
        when(adapter.isPowered()).thenReturn(true);
    }

    public DeviceEmulator addDevice(URL url, String name) {
        DeviceEmulator deviceEmulator = new DeviceEmulator(url, name);
        devices.put(url, deviceEmulator);
        return deviceEmulator;
    }

    public DeviceEmulator addDevice(URL url) {
        return addDevice(url, url.getDeviceAddress());
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public Collection<DeviceEmulator> getDeviceEmulators() {
        return devices.values();
    }
}
