package org.sputnikdev.bluetooth.manager.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.ValueListener;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

public class PinCodeAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(PinCodeAuthenticationProvider.class);

    private final String pinCodeServiceUUID;
    private final String pinCodeCharacteristicUUID;
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
    private CompletableFuture<Void> authFuture;
    private final ReentrantLock lock = new ReentrantLock();

    public PinCodeAuthenticationProvider(String pinCodeServiceUUID, String pinCodeCharacteristicUUID) {
        this.pinCodeServiceUUID = pinCodeServiceUUID;
        this.pinCodeCharacteristicUUID = pinCodeCharacteristicUUID;
    }

    public PinCodeAuthenticationProvider(String pinCodeServiceUUID, String pinCodeCharacteristicUUID, byte[] pinCode) {
        this.pinCodeServiceUUID = pinCodeServiceUUID;
        this.pinCodeCharacteristicUUID = pinCodeCharacteristicUUID;
        this.pinCode = Arrays.copyOf(pinCode, pinCode.length);
    }

    public PinCodeAuthenticationProvider(String pinCodeServiceUUID, String pinCodeCharacteristicUUID,
                                         byte[] pinCode, byte[] expectedAuthenticationResponse) {
        this.pinCodeServiceUUID = pinCodeServiceUUID;
        this.pinCodeCharacteristicUUID = pinCodeCharacteristicUUID;
        this.pinCode = Arrays.copyOf(pinCode, pinCode.length);
        if (expectedAuthenticationResponse != null) {
            this.expectedAuthenticationResponse =
                    Arrays.copyOf(expectedAuthenticationResponse, expectedAuthenticationResponse.length);
        }
    }

    public String getPinCodeServiceUUID() {
        return pinCodeServiceUUID;
    }

    public String getPinCodeCharacteristicUUID() {
        return pinCodeCharacteristicUUID;
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
        if (lock.tryLock()) {
            try {
                URL pinCodeURL = governor.getURL().copyWith(pinCodeServiceUUID, pinCodeCharacteristicUUID);
                int timeout = bluetoothManager.getRefreshRate() * 2;
                CharacteristicGovernor pinCodeChar = bluetoothManager.getCharacteristicGovernor(pinCodeURL);
                // we are enabling notification anyway,
                // some devices require it to be enabled even if there is not any expected auth result
                pinCodeChar.addValueListener(authListener);
                try {
                    logger.debug("Commencing authentication procedure: {}", pinCodeURL);
                    authFuture = pinCodeChar.doWhen(this::readyForAuthentication, gov -> {
                        performAuthentication(gov, timeout);
                    });
                    authFuture.get(timeout, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    throw new BluetoothAuthenticationException("Could not authenticate. Timeout: " + pinCodeURL, e);
                } catch (Exception ex) {
                    throw new BluetoothAuthenticationException("Could not authenticate: "
                            + pinCodeURL + "; Error: " + ex.getMessage(), ex);
                } finally {
                    if (authFuture != null && !authFuture.isDone()) {
                        authFuture.cancel(true);
                        authFuture = null;
                    }
                }
            } finally {
                lock.unlock();
            }
        } else {
            logger.warn("Authentication procedure has already been commenced. Skipping this time: {}",
                    governor.getURL());
        }
    }

    void performAuthentication(CharacteristicGovernor pinCodeChar, int timeout) {
        URL pinCodeURL = pinCodeChar.getURL();
        if (expectedAuthenticationResponse != null) {
            logger.debug("Performing complex authentication with response verification: {}", pinCodeURL);

            if (!pinCodeChar.isNotifiable()) {
                throw new IllegalStateException("Complex authentication requested, "
                        + "but the authentication characteristic "
                        + "does not support notifications: " + pinCodeURL);
            }

            logger.debug("Sending pin code to device: {} ", pinCodeURL);
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
                        + "expected response: {}", pinCodeURL);
                if (!Arrays.equals(expectedAuthenticationResponse, authResponse)) {
                    throw new BluetoothAuthenticationException(
                            "Device sent unexpected authentication response. "
                                    + "Not authorised: " + pinCodeURL);
                }
                logger.debug("Authentication succeeded. "
                        + "Authentication response matches to the expected response: {}", pinCodeURL);
            } else {
                throw new BluetoothAuthenticationException(
                        "Could not receive auth response. Timeout happened: " + pinCodeURL);
            }
        } else {
            logger.debug("Performing simple authentication. Sending pin code to device: {}", pinCodeChar.getURL());
            if (!pinCodeChar.write(pinCode)) {
                throw new BluetoothAuthenticationException("Could not send pin code: " + pinCodeURL);
            }
            logger.debug("Authentication succeeded. Pin code has been sent: {}", pinCodeURL);
        }
    }

    private boolean readyForAuthentication(CharacteristicGovernor gov) {
        boolean ready = gov.isReady() && (!gov.isNotifiable() || gov.isNotifying());
        logger.debug("Checking if characteristic is ready for authentication: {} : {}", gov.getURL(), ready);
        return ready;
    }

}
