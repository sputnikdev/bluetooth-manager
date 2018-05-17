package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CombinedGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.util.BluetoothEmulator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class BluetoothManagerIntegrationTest {

    private static final URL ADAPTER_URL = new URL("bluegiga://12:34:56:78:90:12");
    private static final URL DEVICE_URL = ADAPTER_URL.copyWithDevice("11:22:33:44:55:66");

    private final BluetoothEmulator bluetoothEmulator = new BluetoothEmulator(DEVICE_URL);

    private BluetoothManager bluetoothManager = new BluetoothManagerBuilder()
            .withDiscovering(true)
            .withRediscover(true)
            .withCombinedDevices(true)
            .withRefreshRate(2)
            .withDiscoveryRate(10)
            .build();

    @Before
    public void setUp() {
        bluetoothEmulator.setUp();
        bluetoothManager.registerFactory(bluetoothEmulator.getObjectFactory());
    }

    @Test
    public void testConnectAndAuthenticate() throws Exception {
        DeviceGovernor deviceGovernor = bluetoothManager.getDeviceGovernor(DEVICE_URL.copyWithAdapter(CombinedGovernor.COMBINED_ADDRESS));
        assertNotNull(deviceGovernor);

        deviceGovernor.setAuthenticationProvider(bluetoothEmulator.getPinCodeAuthenticationProvider());

        long delay = 500;

        bluetoothEmulator.scheduleRssiEvent(1000);

        bluetoothEmulator.mockConnect(delay, () -> {
            bluetoothEmulator.scheduleConnectionEvent(delay, true, () -> {
                bluetoothEmulator.scheduleServicesResolvedEvent(delay, true, () -> {
                });
            });
        });

        bluetoothEmulator.mockWriteAuthCharacteristic(delay, () -> {
            bluetoothEmulator.scheduleAuthResponse(delay, BluetoothEmulator.SUCCESSFUL_AUTH_RESPONSE);
        });

        deviceGovernor.setConnectionControl(true);

        bluetoothEmulator.scheduleDeviceDiscovery(0, () -> { });

        deviceGovernor.doWhen(DeviceGovernor::isAuthenticated, gov -> {
            assertTrue(deviceGovernor.isAuthenticated());
        }).join();

        // verify
        Device device = bluetoothEmulator.getDevice();
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

}
