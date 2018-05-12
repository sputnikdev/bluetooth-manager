package org.sputnikdev.bluetooth.manager.util;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.auth.PinCodeAuthenticationProvider;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class BluetoothEmulator {

    public static final byte[] PIN_CODE = { 0x11, 0x44 };
    public static final byte[] SUCCESSFUL_AUTH_RESPONSE = { 0x77, 0x77 };
    public static final byte[] FAILED_AUTH_RESPONSE = { 0x78, 0x78 };

    @Mock
    private BluetoothObjectFactory objectFactory;
    @Mock
    private DiscoveredAdapter discoveredAdapter;
    @Mock
    private Adapter adapter;
    @Mock
    private Device device;
    @Mock
    private Characteristic pinCodeCharacteristic;

    @Captor
    private ArgumentCaptor<Notification<Boolean>> connectionNotificationCaptor;
    @Captor
    private ArgumentCaptor<Notification<Boolean>> serviceResolvedNotificationCaptor;
    @Captor
    private ArgumentCaptor<Notification<byte[]>> authResponseNotificationCaptor;
    @Captor
    private ArgumentCaptor<Notification<Short>> rssiNotificationCaptor;

    private final URL deviceURL;
    private final URL pinCodeCharacteristicURL;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public BluetoothEmulator(URL deviceURL) {
        this.deviceURL = deviceURL;
        pinCodeCharacteristicURL = deviceURL.copyWith("0000eee1-0000-1000-8000-00805f9b34fb",
                "0000eee3-0000-1000-8000-00805f9b34fb");
    }

    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(objectFactory.getProtocolName()).thenReturn("bluegiga");

        when(discoveredAdapter.getURL()).thenReturn(deviceURL.getAdapterURL());
        when(adapter.getURL()).thenReturn(deviceURL.getAdapterURL());
        when(adapter.isPowered()).thenReturn(true);

        when(device.getURL()).thenReturn(deviceURL);
        doNothing().when(device).enableConnectedNotifications(connectionNotificationCaptor.capture());
        doNothing().when(device).enableServicesResolvedNotifications(serviceResolvedNotificationCaptor.capture());
        doNothing().when(device).enableRSSINotifications(rssiNotificationCaptor.capture());
        when(device.isConnected()).thenReturn(false);

        when(pinCodeCharacteristic.getURL()).thenReturn(pinCodeCharacteristicURL);
        Set<CharacteristicAccessType> flags = Stream.of(CharacteristicAccessType.WRITE, CharacteristicAccessType.NOTIFY).collect(Collectors.toSet());
        when(pinCodeCharacteristic.getFlags()).thenReturn(flags);
        when(pinCodeCharacteristic.writeValue(PIN_CODE)).thenReturn(true);
        doNothing().when(pinCodeCharacteristic).enableValueNotifications(authResponseNotificationCaptor.capture());

        when(objectFactory.getDiscoveredAdapters()).thenReturn(Stream.of(discoveredAdapter).collect(Collectors.toSet()));
        when(objectFactory.getAdapter(deviceURL.getAdapterURL())).thenReturn(adapter);

        when(objectFactory.getCharacteristic(pinCodeCharacteristicURL)).thenReturn(pinCodeCharacteristic);
    }

    public BluetoothObjectFactory getObjectFactory() {
        doAnswer(answer -> {
            when(pinCodeCharacteristic.isNotifying()).thenReturn(true);
            return true;
        }).when(pinCodeCharacteristic).enableValueNotifications(authResponseNotificationCaptor.capture());
        return objectFactory;
    }

    public PinCodeAuthenticationProvider getPinCodeAuthenticationProvider() {
        return new PinCodeAuthenticationProvider(pinCodeCharacteristicURL.getServiceUUID(),
                pinCodeCharacteristicURL.getCharacteristicUUID(), PIN_CODE, SUCCESSFUL_AUTH_RESPONSE);
    }

    public void mockWriteAuthCharacteristic(long delay, Runnable andThen) {
        doAnswer(answer -> {
            Thread.sleep(delay);
            CompletableFuture.runAsync(andThen);
            return true;
        }).when(pinCodeCharacteristic).writeValue(PIN_CODE);
    }

    public void scheduleDeviceDiscovery(long delay, Runnable andThen) {
        scheduler.schedule(() -> {
            when(objectFactory.getDevice(deviceURL)).thenReturn(device);
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void scheduleAuthResponse(long delay, byte[] response) {
        scheduler.schedule(() -> {
            authResponseNotificationCaptor.getValue().notify(response);
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void scheduleRssiEvent(long time) {
        scheduler.scheduleAtFixedRate(() -> {
            if (!rssiNotificationCaptor.getAllValues().isEmpty()) {
                rssiNotificationCaptor.getValue().notify((short) ThreadLocalRandom.current().nextInt(-70, -60));
            }
        }, 0, time, TimeUnit.MILLISECONDS);
    }

    public void mockConnect(long delay, Runnable andThen) {
        doAnswer(answer -> {
            Thread.sleep(delay);
            when(device.isConnected()).thenReturn(true);
            CompletableFuture.runAsync(andThen);
            return true;
        }).when(device).connect();
    }

    public void mockDisconnect(long delay, Runnable andThen) {
        doAnswer(answer -> {
            Thread.sleep(delay);
            when(device.isConnected()).thenReturn(false);
            CompletableFuture.runAsync(andThen);
            return true;
        }).when(device).disconnect();
    }

    public void scheduleConnectionEvent(long delay, boolean connected, Runnable andThen) {
        scheduler.schedule(() -> {
            connectionNotificationCaptor.getValue().notify(connected);
            CompletableFuture.runAsync(andThen);
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void scheduleServicesResolvedEvent(long delay, boolean servicesResolved, Runnable andThen) {
        scheduler.schedule(() -> {
            serviceResolvedNotificationCaptor.getValue().notify(servicesResolved);
            CompletableFuture.runAsync(andThen);
        }, delay, TimeUnit.MILLISECONDS);
    }


    public Device getDevice() {
        return device;
    }
}
