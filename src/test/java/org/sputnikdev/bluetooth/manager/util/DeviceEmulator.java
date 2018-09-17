package org.sputnikdev.bluetooth.manager.util;

import org.mockito.ArgumentCaptor;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceEmulator {

    private Device device;
    private DiscoveredDevice discoveredDevice;
    private Map<URL, CharacteristicEmulator> characteristics = new HashMap<>();
    private ScheduledFuture<?> rssiFuture;
    private ArgumentCaptor<Notification> rssiNotificationCaptor = ArgumentCaptor.forClass(Notification.class);
    private ArgumentCaptor<Notification> connectionNotificationCaptor = ArgumentCaptor.forClass(Notification.class);
    private ArgumentCaptor<Notification> servicesResolvedNotificationCaptor = ArgumentCaptor.forClass(Notification.class);

    public DeviceEmulator(URL url) {
        this(url, url.getDeviceAddress());
    }

    public DeviceEmulator(URL url, String name) {
        device = mock(Device.class);
        discoveredDevice = mock(DiscoveredDevice.class);
        when(device.getURL()).thenReturn(url);
        when(device.getName()).thenReturn(name);

        when(device.getServices()).thenAnswer(answer ->
                characteristics.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().getServiceURL()))
                        .entrySet().stream().map(service -> new Service() {
            @Override
            public URL getURL() {
                return service.getKey();
            }

            @Override
            public List<Characteristic> getCharacteristics() {
                return service.getValue().stream().map(entry -> entry.getValue().getCharacteristic()).collect(Collectors.toList());
            }
        }).collect(Collectors.toList()));

        discoveredDevice = new DiscoveredDevice(url, name, null, (short) 0, 1, true) {
            @Override
            public short getRSSI() {
                return device.getRSSI();
            }
        };

        doNothing().when(device).enableRSSINotifications(rssiNotificationCaptor.capture());
        doNothing().when(device).enableConnectedNotifications(connectionNotificationCaptor.capture());
        doNothing().when(device).enableServicesResolvedNotifications(servicesResolvedNotificationCaptor.capture());

        doAnswer(answer -> {
            when(device.isConnected()).thenReturn(true);
            CompletableFuture.runAsync(() -> {
                connectionNotificationCaptor.getValue().notify(true);
                CompletableFuture.runAsync(() -> {
                    when(device.isServicesResolved()).thenReturn(true);
                    servicesResolvedNotificationCaptor.getValue().notify(true);
                });
            });
            return true;
        }).when(device).connect();
    }

    public Device getDevice() {
        return device;
    }

    public Collection<CharacteristicEmulator> getCharacteristics() {
        return characteristics.values();
    }

    public CharacteristicEmulator addCharacteristic(URL url, CharacteristicAccessType... flags) {
        CharacteristicEmulator characteristic = new CharacteristicEmulator(url, flags);
        characteristics.put(url, characteristic);
        return characteristic;
    }

    public void mockConnection(long delay, Runnable andThen) {
        doAnswer(answer -> {
            Thread.sleep(delay);
            when(device.isConnected()).thenReturn(true);
            CompletableFuture.runAsync(andThen);
            return true;
        }).when(device).connect();
    }

    public void scheduleRandomRSSI(long delay) {
        if (rssiFuture != null) {
            rssiFuture.cancel(true);
        }

        rssiFuture = BluetoothFactoryEmulator.scheduler.scheduleWithFixedDelay((Runnable) () -> {
            short rssi = (short) -BluetoothFactoryEmulator.random.nextInt(100);
            when(device.getRSSI()).thenReturn(rssi);
            if (!rssiNotificationCaptor.getAllValues().isEmpty()) {
                rssiNotificationCaptor.getValue().notify(rssi);
            }
        }, 0, delay, TimeUnit.SECONDS);
    }

    public void cancelRSSI() {
        if (rssiFuture != null) {
            rssiFuture.cancel(true);
            rssiFuture = null;
        }
    }

    public DiscoveredDevice getDiscoveredDevice() {
        return discoveredDevice;
    }

}
