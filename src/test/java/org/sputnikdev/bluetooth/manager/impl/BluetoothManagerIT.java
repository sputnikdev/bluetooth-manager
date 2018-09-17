package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Test;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.auth.PinCodeAuthenticationProvider;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.util.AdapterEmulator;
import org.sputnikdev.bluetooth.manager.util.BluetoothFactoryEmulator;
import org.sputnikdev.bluetooth.manager.util.CharacteristicEmulator;
import org.sputnikdev.bluetooth.manager.util.DeviceEmulator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BluetoothManagerIT {

    private static final URL ADAPTER_URL = new URL("bluegiga:/12:34:56:78:90:12");
    private static final URL DEVICE_URL = ADAPTER_URL.copyWithDevice("4C:65:00:D0:7A:EE");
    private static final URL PIN_CODE_CHAR_URL = DEVICE_URL.copyWith("0000eee1-0000-1000-8000-00805f9b34fb",
            "0000eee3-0000-1000-8000-00805f9b34fb");
    public static final byte[] PIN_CODE = { 0x11, 0x44 };
    public static final byte[] SUCCESSFUL_AUTH_RESPONSE = { 0x77, 0x77 };
    public static final byte[] FAILED_AUTH_RESPONSE = { 0x78, 0x78 };

    private BluetoothManager bluetoothManager = new BluetoothManagerBuilder()
            .withDiscovering(true)
            .withRediscover(true)
            .withCombinedDevices(true)
            .withRefreshRate(1)
            .withDiscoveryRate(1)
            .build();

    @Test
    public void testConnectAndAuthenticate() throws Exception {

        BluetoothFactoryEmulator factory = new BluetoothFactoryEmulator("bluegiga");
        DeviceEmulator deviceEmulator = factory.addAdapter(ADAPTER_URL).addDevice(DEVICE_URL);
        deviceEmulator.scheduleRandomRSSI(1);
        CharacteristicEmulator pinCodeChar =
                deviceEmulator.addCharacteristic(PIN_CODE_CHAR_URL,
                        CharacteristicAccessType.NOTIFY, CharacteristicAccessType.WRITE_WITHOUT_RESPONSE);
        pinCodeChar.whenWritten(PIN_CODE, () -> pinCodeChar.notify(SUCCESSFUL_AUTH_RESPONSE));

        bluetoothManager.registerFactory(factory.getFactory());

        DeviceGovernor deviceGovernor = bluetoothManager.getDeviceGovernor(DEVICE_URL.copyWithAdapter(CombinedGovernor.COMBINED_ADDRESS));
        assertNotNull(deviceGovernor);

        deviceGovernor.setAuthenticationProvider(new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, SUCCESSFUL_AUTH_RESPONSE));

        deviceGovernor.setConnectionControl(true);

        deviceGovernor.doWhen(DeviceGovernor::isAuthenticated, gov -> {
            assertTrue(deviceGovernor.isAuthenticated());
        }).join();

        // verify
        Device device = deviceEmulator.getDevice();
        verify(device).getAlias();
        verify(device).getName();
        verify(device).isBleEnabled();
        verify(device).getBluetoothClass();

        verify(device).enableConnectedNotifications(any());
        verify(device).enableServicesResolvedNotifications(any());
        verify(device).enableBlockedNotifications(any());
        verify(device).enableRSSINotifications(any());
        verify(device).enableServiceDataNotifications(any());
        verify(device).enableManufacturerDataNotifications(any());

        verify(device, atLeastOnce()).getServices();
        verify(device, atLeastOnce()).getTxPower();
        verify(device, atLeastOnce()).getRSSI();
        verify(device, atLeastOnce()).isBlocked();
        verify(device, atLeastOnce()).isConnected();
        verify(device, atLeastOnce()).isServicesResolved();

    }

    @Test
    public void testBeaconDynamicDeviceAddress() throws Exception {

        URL privateDeviceURL = new URL("/XX:XX:XX:XX:XX:XX/[name=PrivateName]");

        BluetoothFactoryEmulator factory = new BluetoothFactoryEmulator("bluegiga");

        AdapterEmulator adapterEmulator = factory.addAdapter(ADAPTER_URL);

        // Non resolvable device MAC
        DeviceEmulator deviceEmulator =
                adapterEmulator.addDevice(ADAPTER_URL.copyWithDevice("5C:80:AE:CA:01:DE"), "PrivateName");

        deviceEmulator.scheduleRandomRSSI(1);

        bluetoothManager.registerFactory(factory.getFactory());


        bluetoothManager.addDeviceDiscoveryListener(device -> {
            System.out.println(device.getURL() + " " + device.getRSSI());
        });

        DeviceGovernor privateDevice = bluetoothManager.getDeviceGovernor(privateDeviceURL);

        privateDevice.setOnlineTimeout(2);

        privateDevice.doWhen(DeviceGovernor::isOnline, gov -> {
            assertTrue(privateDevice.isOnline());
        }).join();

        // this would indicate that the governor was never reset
        verify(deviceEmulator.getDevice(), never()).disableRSSINotifications();

        // now, native device changes its address
        deviceEmulator.cancelRSSI();
        // Also non resolvable device MAC
        DeviceEmulator changedMacDevice =
                adapterEmulator.addDevice(ADAPTER_URL.copyWithDevice("73:11:20:83:78:09"), "PrivateName");
        changedMacDevice.scheduleRandomRSSI(1);

        Thread.sleep(3000);

        // check if we still online
        assertTrue(privateDevice.isOnline());

        // this would indicate that the governor was reset after the device changed its address
        verify(deviceEmulator.getDevice()).disableRSSINotifications();

    }

}
