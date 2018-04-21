package org.sputnikdev.bluetooth.manager.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.ValueListener;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PinCodeAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(PinCodeAuthenticationProvider.class);

    private final URL pinCodeURL;
    private byte[] pinCode;
    private byte[] expectedAuthenticationResponse;
    private CountDownLatch authResponseLatch;
    private byte[] authResponse;
    private final ValueListener authListener = data -> {
        authResponse = data;
        if (authResponseLatch != null) {
            authResponseLatch.countDown();
        }
    };

    public PinCodeAuthenticationProvider(URL pinCodeURL) {
        this.pinCodeURL = pinCodeURL;
    }

    public PinCodeAuthenticationProvider(URL pinCodeURL, byte[] pinCode) {
        this.pinCodeURL = pinCodeURL;
        this.pinCode = Arrays.copyOf(pinCode, pinCode.length);
    }

    public PinCodeAuthenticationProvider(URL pinCodeURL, byte[] pinCode, byte[] expectedAuthenticationResponse) {
        this.pinCodeURL = pinCodeURL;
        this.pinCode = Arrays.copyOf(pinCode, pinCode.length);
        this.expectedAuthenticationResponse =
                Arrays.copyOf(expectedAuthenticationResponse, expectedAuthenticationResponse.length);
    }

    public URL getPinCodeURL() {
        return pinCodeURL;
    }

    public byte[] getPinCode() {
        return Arrays.copyOf(pinCode, pinCode.length);
    }

    public void setPinCode(byte[] pinCode) {
        this.pinCode = Arrays.copyOf(pinCode, pinCode.length);
    }

    public byte[] getExpectedAuthenticationResponse() {
        if (expectedAuthenticationResponse == null) {
            return null;
        }
        return Arrays.copyOf(expectedAuthenticationResponse, expectedAuthenticationResponse.length);
    }

    public void setExpectedAuthenticationResponse(byte[] expectedAuthenticationResponse) {
        if (expectedAuthenticationResponse == null) {
            this.expectedAuthenticationResponse = null;
        } else {
            this.expectedAuthenticationResponse =
                    Arrays.copyOf(expectedAuthenticationResponse, expectedAuthenticationResponse.length);
        }
    }

    @Override
    public void authenticate(BluetoothManager bluetoothManager, DeviceGovernor governor)
            throws BluetoothAuthenticationException {

        int timeout = bluetoothManager.getRefreshRate() * 2;
        CharacteristicGovernor pinCodeChar = bluetoothManager.getCharacteristicGovernor(pinCodeURL);
        // we are enabling notification anyway,
        // some devices require it to be enabled even if there is not any expected auth result
        pinCodeChar.addValueListener(authListener);

        try {
            logger.debug("Commencing authentication procedure: {}", pinCodeURL);
            pinCodeChar.<CharacteristicGovernor>doWhen(BluetoothGovernor::isReady, gov -> {
                performAuthentication(gov, timeout);
            }).get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BluetoothAuthenticationException("Could not authenticate. Timeout: " + pinCodeURL, e);
        } catch (Exception ex) {
            throw new BluetoothAuthenticationException("Could not authenticate: "
                    + pinCodeURL + "; Error: " + ex.getMessage(), ex);
        }

    }

    private void performAuthentication(CharacteristicGovernor pinCodeChar, int timeout) {
        if (expectedAuthenticationResponse != null) {
            logger.debug("Performing complex authentication with response verification: {}", pinCodeURL);

            if (!pinCodeChar.isNotifiable()) {
                throw new IllegalStateException("Complex authentication requested, "
                        + "but the authentication characteristic "
                        + "does not support notifications: " + pinCodeURL);
            }

            try {
                pinCodeChar.doWhen(CharacteristicGovernor::isNotifying, gov -> {
                    logger.debug("Successfully subscribed to the authentication characteristic: {} ", pinCodeURL);
                    authResponseLatch = new CountDownLatch(1);
                    if (!pinCodeChar.write(pinCode)) {
                        throw new BluetoothAuthenticationException("Could not send pin code: " + pinCodeURL);
                    }
                    try {
                        logger.debug("Waiting for a response from the authentication characteristic: {}", pinCodeURL);
                        authResponseLatch.await(timeout, TimeUnit.SECONDS);
                    } catch (InterruptedException ignore) { /* ignore */ }
                    if (authResponse != null) {
                        logger.debug("Authentication response has been received. Checking if it matches to the "
                                + "expected response: {}", pinCodeChar);
                        if (!Arrays.equals(expectedAuthenticationResponse, authResponse)) {
                            throw new BluetoothAuthenticationException(
                                    "Device sent unexpected authentication response. "
                                            + "Not authorised: " + pinCodeURL);
                        }
                        logger.debug("Authentication succeeded. "
                                + "Authentication response matches to the expected response: {}", pinCodeChar);
                    } else {
                        throw new BluetoothAuthenticationException(
                                "Could not receive auth response. Timeout happened: " + pinCodeURL);
                    }
                }).get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new BluetoothAuthenticationException("Complex authentication: Could not authenticate. "
                        + "Timeout: " + pinCodeURL, e);
            } catch (Exception ex) {
                throw new BluetoothAuthenticationException("Complex authentication: Could not authenticate: "
                        + pinCodeURL + "; Error: " + ex.getMessage(), ex);
            }
        } else {
            logger.debug("Performing simple authentication: {}", pinCodeChar.getURL());
            if (!pinCodeChar.write(pinCode)) {
                throw new BluetoothAuthenticationException("Could not send pin code: " + pinCodeURL);
            }
            logger.debug("Authentication succeeded. Pin code has been sent: {}", pinCodeURL);
        }
    }

}
