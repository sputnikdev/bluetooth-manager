package org.sputnikdev.bluetooth.manager.transport;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Characteristic properties (access type).
 * Read the spec here: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.attribute.gatt.characteristic_declaration.xml
 */
public enum CharacteristicAccessType {

    BROADCAST(0x01),
    READ(0x02),
    WRITE_WITHOUT_RESPONSE(0x04),
    WRITE(0x08),
    NOTIFY(0x10),
    INDICATE(0x20),
    AUTHENTICATED_SIGNED_WRITES(0x40),
    EXTENDED_PROPERTIES(0x80);

    int bitField;

    CharacteristicAccessType(int bitField) {
        this.bitField = bitField;
    }

    public int getBitField() {
        return bitField;
    }

    public static CharacteristicAccessType fromBitField(int bitField) {
        return Stream.of(CharacteristicAccessType.values())
                .filter(c -> c.bitField == bitField)
                .findFirst().orElse(null);
    }

    public static Set<CharacteristicAccessType> parse(int flags) {
        return Stream.of(CharacteristicAccessType.values())
                .filter(c -> (c.bitField & flags) > 0)
                .collect(Collectors.toSet());
    }

}
