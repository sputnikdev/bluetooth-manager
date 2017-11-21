package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(fullyQualifiedNames = {"org.sputnikdev.bluetooth.manager.impl.BluetoothManagerFactory"})
public class BluetoothObjectFactoryProviderTest {

    private static final String PROTOCOL_NAME = "tinyb";

    @Mock
    private BluetoothManagerImpl bluetoothManager;

    @Mock
    private BluetoothObjectFactory objectFactory;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(BluetoothManagerFactory.class);
        when(BluetoothManagerFactory.getManager()).thenReturn(bluetoothManager);
        when(objectFactory.getProtocolName()).thenReturn(PROTOCOL_NAME);
    }

    @Test
    public void testRegisterFactory() throws Exception {

    }

    @Test
    public void testUnregisterFactory() throws Exception {
        BluetoothObjectFactoryProvider.unregisterFactory(objectFactory);
        verify(bluetoothManager, times(1)).handleObjectFactoryUnregistered(objectFactory);
    }
}
