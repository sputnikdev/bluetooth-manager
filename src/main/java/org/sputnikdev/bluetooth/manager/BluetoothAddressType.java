package org.sputnikdev.bluetooth.manager;

/**
 * The fundamental identifier of a Bluetooth® Low-Energy device, similar to an Ethernet or Wi-Fi® Media Access Control
 * (MAC) address is the Bluetooth Device Address. This 48-bit (6-byte) number uniquely identifies a device among peers.
 *
 * @author Vlad Kolotov
 */
public enum BluetoothAddressType {

    /**
     * Address type is unknown.
     */
    UNKNOWN,
    /**
     * This is the standard, IEEE-assigned 48-bit universal LAN MAC address which must be obtained from the
     * IEEE Registration Authority.
     */
    PUBLIC,
    /**
     * Random type address. BLE standard adds the ability to periodically change the address to insure device privacy.
     * <p>Two random types are provided:
     * <ul>
     *     <li>Static Address. A 48-bit randomly generated address. A new value is generated after each power cycle.
     *     </li>
     *     <li>Private Address. When a device wants to remain private, it uses private addresses. These are addresses
     *     that can be periodically changed so that the device can not be tracked. These may be resolvable or not.</li>
     * </ul>
     */
    RANDOM

}
