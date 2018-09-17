package org.sputnikdev.bluetooth.manager.util;

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BluetoothFactoryEmulator {

    private BluetoothObjectFactory objectFactory;
    private Map<DiscoveredAdapter, AdapterEmulator> adapters = new HashMap<>();

    static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    static final Random random = new Random();

    public BluetoothFactoryEmulator(String factoryName) {
        objectFactory = mock(BluetoothObjectFactory.class);
        when(objectFactory.getProtocolName()).thenReturn(factoryName);
        when(objectFactory.getDiscoveredAdapters()).thenAnswer(answer -> adapters.keySet());

        when(objectFactory.getDiscoveredDevices()).thenAnswer(answer -> adapters.values().stream()
                .flatMap(adapter -> adapter.getDeviceEmulators().stream())
                .map(DeviceEmulator::getDiscoveredDevice).collect(Collectors.toSet()));

        when(objectFactory.getAdapter(any(URL.class))).thenAnswer(answer -> adapters.values().stream()
                .filter(adapter -> adapter.getAdapter().getURL().equals(answer.getArgumentAt(0, URL.class)))
                .findFirst().map(AdapterEmulator::getAdapter).orElse(null));

        when(objectFactory.getDevice(any(URL.class))).thenAnswer(answer -> adapters.values().stream()
                .flatMap(adapter -> adapter.getAdapter().getDevices().stream())
                .filter(device -> device.getURL().equals((answer.getArgumentAt(0, URL.class))))
                .findFirst().orElse(null));

        when(objectFactory.getCharacteristic(any(URL.class))).thenAnswer(answer -> adapters.values().stream()
                .flatMap(adapter -> adapter.getAdapter().getDevices().stream())
                .flatMap(device -> device.getServices().stream())
                .flatMap(service -> service.getCharacteristics().stream())
                .filter(characteristic -> characteristic.getURL().equals((answer.getArgumentAt(0, URL.class))))
                .findFirst().orElse(null));
    }

    public AdapterEmulator addAdapter(URL url) {
        return addAdapter(url, url.getAdapterAddress());
    }

    public AdapterEmulator addAdapter(URL url, String name) {
        AdapterEmulator adapterEmulator = new AdapterEmulator(url, name);
        DiscoveredAdapter discoveredAdapter = new DiscoveredAdapter(url, name, null);
        adapters.put(discoveredAdapter, adapterEmulator);
        return adapterEmulator;
    }

    public BluetoothObjectFactory getFactory() {
        return objectFactory;
    }
}
