package com.acuity.iot.dsa.dslink.io.msgpack;

/**
 * @author Aaron Hansen
 */
interface MsgpackConstants {

    //byte POSFIXINT_MASK = (byte) 0x80;
    byte FIXMAP_PREFIX = (byte) 0x80;
    byte FIXLIST_PREFIX = (byte) 0x90;
    byte FIXSTR_PREFIX = (byte) 0xa0;
    byte NULL = (byte) 0xc0;
    //byte NEVER_USED = (byte) 0xc1;
    byte FALSE = (byte) 0xc2;
    byte TRUE = (byte) 0xc3;
    byte BIN8 = (byte) 0xc4;
    byte BIN16 = (byte) 0xc5;
    byte BIN32 = (byte) 0xc6;
    //byte EXT8 = (byte) 0xc7;
    //byte EXT16 = (byte) 0xc8;
    //byte EXT32 = (byte) 0xc9;
    byte FLOAT32 = (byte) 0xca;
    byte FLOAT64 = (byte) 0xcb;
    byte UINT8 = (byte) 0xcc;
    byte UINT16 = (byte) 0xcd;
    byte UINT32 = (byte) 0xce;
    byte UINT64 = (byte) 0xcf;
    byte INT8 = (byte) 0xd0;
    byte INT16 = (byte) 0xd1;
    byte INT32 = (byte) 0xd2;
    byte INT64 = (byte) 0xd3;
    byte STR8 = (byte) 0xd9;
    byte STR16 = (byte) 0xda;
    byte STR32 = (byte) 0xdb;
    byte LIST16 = (byte) 0xdc;
    byte LIST32 = (byte) 0xdd;
    byte MAP16 = (byte) 0xde;
    byte MAP32 = (byte) 0xdf;
    //byte NEGFIXINT_PREFIX = (byte) 0xe0;

}
