package org.sputnikdev.bluetooth.manager.transport;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType.*;


public class CharacteristicAccessTypeTest {

    @Test
    public void testGetBitField() throws Exception {
        assertEquals(0x01, BROADCAST.getBitField());
        assertEquals(0x02, READ.getBitField());
        assertEquals(0x04, WRITE_WITHOUT_RESPONSE.getBitField());
        assertEquals(0x08, WRITE.getBitField());
        assertEquals(0x10, NOTIFY.getBitField());
        assertEquals(0x20, INDICATE.getBitField());
        assertEquals(0x40, AUTHENTICATED_SIGNED_WRITES.getBitField());
        assertEquals(0x80, EXTENDED_PROPERTIES.getBitField());
    }

    @Test
    public void testFromBitField() throws Exception {
        assertEquals(BROADCAST, fromBitField(0b00000001));
        assertEquals(READ, fromBitField(0b00000010));
        assertEquals(WRITE_WITHOUT_RESPONSE, fromBitField(0b00000100));
        assertEquals(WRITE, fromBitField(0b00001000));
        assertEquals(NOTIFY, fromBitField(0b00010000));
        assertEquals(INDICATE, fromBitField(0b00100000));
        assertEquals(AUTHENTICATED_SIGNED_WRITES, fromBitField(0b01000000));
        assertEquals(EXTENDED_PROPERTIES, fromBitField(0b10000000));
    }

    @Test
    public void testParse() throws Exception {
        int notify = 0b00010000;
        Set<CharacteristicAccessType> actual = parse(notify);
        assertEquals(1, actual.size());
        assertTrue(actual.contains(NOTIFY));

        int notifyAndRead = 0b00010010;
        actual = parse(notifyAndRead);
        assertEquals(2, actual.size());
        assertTrue(actual.contains(NOTIFY));
        assertTrue(actual.contains(READ));

        int notifyReadAndWrite = 0b00011010;
        actual = parse(notifyReadAndWrite);
        assertEquals(3, actual.size());
        assertTrue(actual.contains(NOTIFY));
        assertTrue(actual.contains(READ));
        assertTrue(actual.contains(WRITE));
    }
}