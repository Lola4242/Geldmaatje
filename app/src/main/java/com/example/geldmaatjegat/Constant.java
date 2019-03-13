package com.example.geldmaatjegat;


public final class Constant {

    public static final byte BALANCE = (byte) 0x20;
    public static final int BALANCE_LENGTH = 4;

    public static final byte TIME = (byte) 0x02;
    public static final int TIME_LENGTH = 4;

    public static final byte BED_LIGHT = (byte) 0x40;
    public static final byte BED_LIGHT_TIME_LENGTH = 4;
    public static final byte BED_LIGHT_DURATION_LENGTH = 2;

    public static final byte ALARM = (byte) 0x42;
    public static final byte ALARM_TIME_LENGTH = 4;

    public static final byte ALLOWENCE = (byte) 0x43;
    public static final byte ALLOWENCE_TIME_LENGTH = 5;

    public static final byte SOUND = (byte) 0x41;

    public static final byte ON = (byte) 0x01;
    public static final byte OFF = (byte) 0x00;

    public static final String TIME_ZONE = "Etc/GMT+1";

    public static final byte IDENTITY = (byte) 0x8c;
    public static final byte SQUIRREL = (byte) 0x02;
    public static final byte HIPPO = (byte) 0x01;

    public static final byte PICTURE_TRANSFER = (byte) 0x44;
    public static final byte PICTURE_TRANSFER_LENGTH = 3;
    public static final byte FIRMWARE_TRANSFER = (byte) 0x45;
    public static final byte FIRMWARE_TRANSFER_LENGTH = 2;
    public static final byte SOUND_TRANSFER = (byte) 0x46;


}
