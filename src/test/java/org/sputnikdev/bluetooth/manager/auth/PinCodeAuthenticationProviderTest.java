package org.sputnikdev.bluetooth.manager.auth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.ValueListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PinCodeAuthenticationProviderTest {

    private static final URL PIN_CODE_CHAR_URL = new URL("/XX:XX:XX:XX:XX:XX/11:22:33:44:55:66/eee1/eee3");
    private static final int REFRESH_RATE = 5;
    private static final byte[] PIN_CODE = {0x11, 0x44};
    private static final byte[] SUCCESSFUL_AUTH_RESPONSE = {0x77, 0x77};
    private static final byte[] FAILED_AUTH_RESPONSE = {0x78, 0x78};

    @Mock
    private BluetoothManager bluetoothManager;
    @Mock
    private DeviceGovernor deviceGovernor;
    @Mock
    private CharacteristicGovernor pinCodeCharacteristic;

    @Captor
    private ArgumentCaptor<Predicate<CharacteristicGovernor>> authConditionCaptor;
    @Captor
    private ArgumentCaptor<Consumer<CharacteristicGovernor>> authTaskCaptor;
    @Captor
    private ArgumentCaptor<ValueListener> authResponseListenerCaptor;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private List<CompletableFuture<Void>> authFutures = new ArrayList<>();

    @Spy
    private PinCodeAuthenticationProvider provider =
            new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                    PIN_CODE_CHAR_URL.getCharacteristicUUID());

    @Before
    public void setUp() {
        when(deviceGovernor.getURL()).thenReturn(PIN_CODE_CHAR_URL.getDeviceURL());
        when(pinCodeCharacteristic.getURL()).thenReturn(PIN_CODE_CHAR_URL);
        when(bluetoothManager.getCharacteristicGovernor(PIN_CODE_CHAR_URL)).thenReturn(pinCodeCharacteristic);
        doNothing().when(pinCodeCharacteristic).addValueListener(authResponseListenerCaptor.capture());
        when(bluetoothManager.getRefreshRate()).thenReturn(REFRESH_RATE);

        when(pinCodeCharacteristic.doWhen(authConditionCaptor.capture(), authTaskCaptor.capture()))
                .thenAnswer(answer -> {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    authFutures.add(future);
                    Executors.newScheduledThreadPool(1).schedule(() -> {
                        try {
                            answer.getArgumentAt(1, Consumer.class).accept(pinCodeCharacteristic);
                            future.complete(null);
                        } catch (Exception ex) {
                            future.completeExceptionally(ex);
                        }
                    }, 200, TimeUnit.MILLISECONDS);
                    return future;
                });
        doNothing().when(provider).performAuthentication(any(CharacteristicGovernor.class), anyInt());
        when(pinCodeCharacteristic.write(PIN_CODE)).thenReturn(true);
        when(pinCodeCharacteristic.isNotifiable()).thenReturn(true);
    }

    @Test
    public void testAuthenticate() {
        when(pinCodeCharacteristic.isNotifiable()).thenReturn(true);

        provider.authenticate(bluetoothManager, deviceGovernor);

        verify(provider).performAuthentication(pinCodeCharacteristic, REFRESH_RATE * 2);
        verify(pinCodeCharacteristic).doWhen(authConditionCaptor.getValue(), authTaskCaptor.getValue());
        verify(pinCodeCharacteristic).addValueListener(authResponseListenerCaptor.getValue());
        verify(bluetoothManager).getRefreshRate();
    }

    @Test
    public void testAuthenticateTimeout() {
        when(bluetoothManager.getRefreshRate()).thenReturn(0);

        expectedEx.expect(BluetoothAuthenticationException.class);
        expectedEx.expectMessage("Could not authenticate. Timeout: " + PIN_CODE_CHAR_URL);

        try {
            provider.authenticate(bluetoothManager, deviceGovernor);
        } finally {
            assertEquals(1, authFutures.size());
            assertTrue(authFutures.get(0).isCancelled());
        }
    }

    @Test
    public void testAuthenticateException() {
        CompletableFuture<Void> authFuture = new CompletableFuture<>();
        RuntimeException ex = new RuntimeException("Unexpected error");
        when(pinCodeCharacteristic.doWhen(authConditionCaptor.capture(), authTaskCaptor.capture())).thenReturn(authFuture);
        Executors.newScheduledThreadPool(1).schedule(() -> authFuture.completeExceptionally(ex), 200, TimeUnit.MILLISECONDS);

        expectedEx.expect(BluetoothAuthenticationException.class);
        expectedEx.expectMessage("Could not authenticate: " + PIN_CODE_CHAR_URL + "; Error: java.lang.RuntimeException: Unexpected error");

        provider.authenticate(bluetoothManager, deviceGovernor);
    }

    @Test
    public void testAuthenticateRepetitive() {
        CompletableFuture<Void> async = CompletableFuture.runAsync(() -> {
            provider.authenticate(bluetoothManager, deviceGovernor);
        });
        provider.authenticate(bluetoothManager, deviceGovernor);
        provider.authenticate(bluetoothManager, deviceGovernor);

        async.join();

        verify(provider, times(2)).performAuthentication(pinCodeCharacteristic, REFRESH_RATE * 2);
    }

    @Test
    public void testPerformAuthenticationWithoutResponse() {
        provider = new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, null);

        provider.performAuthentication(pinCodeCharacteristic, REFRESH_RATE * 2);

        verify(pinCodeCharacteristic).write(PIN_CODE);
    }

    @Test
    public void testPerformAuthenticationWithoutResponseFailToWrite() {
        provider = new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, null);
        when(pinCodeCharacteristic.write(PIN_CODE)).thenReturn(false);

        expectedEx.expect(BluetoothAuthenticationException.class);
        expectedEx.expectMessage("Could not send pin code: " + PIN_CODE_CHAR_URL);

        provider.performAuthentication(pinCodeCharacteristic, REFRESH_RATE * 2);
    }

    @Test
    public void testPerformAuthenticationWithResponse() throws Exception {
        provider = new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, SUCCESSFUL_AUTH_RESPONSE);

        when(pinCodeCharacteristic.isNotifiable()).thenReturn(true);
        doAnswer(answer -> {
            authResponseListenerCaptor.getValue().changed(SUCCESSFUL_AUTH_RESPONSE);
            return true;
        }).when(pinCodeCharacteristic).write(PIN_CODE);

        provider.authenticate(bluetoothManager, deviceGovernor);

        verify(pinCodeCharacteristic).write(PIN_CODE);
    }

    @Test
    public void testPerformAuthenticationWithResponseTimeout() {
        provider = new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, SUCCESSFUL_AUTH_RESPONSE);

        expectedEx.expect(BluetoothAuthenticationException.class);
        expectedEx.expectMessage("Could not receive auth response. Timeout happened: " + PIN_CODE_CHAR_URL);

        provider.performAuthentication(pinCodeCharacteristic, 0);
    }

    @Test
    public void testPerformAuthenticationWithResponseFailedResponse() throws Exception {
        provider = new PinCodeAuthenticationProvider(PIN_CODE_CHAR_URL.getServiceUUID(),
                PIN_CODE_CHAR_URL.getCharacteristicUUID(), PIN_CODE, SUCCESSFUL_AUTH_RESPONSE);

        when(pinCodeCharacteristic.isNotifiable()).thenReturn(true);
        doAnswer(answer -> {
            authResponseListenerCaptor.getValue().changed(FAILED_AUTH_RESPONSE);
            return true;
        }).when(pinCodeCharacteristic).write(PIN_CODE);

        expectedEx.expect(BluetoothAuthenticationException.class);
        expectedEx.expectMessage("Device sent unexpected authentication response. Not authorised: " + PIN_CODE_CHAR_URL);

        provider.authenticate(bluetoothManager, deviceGovernor);

    }

}