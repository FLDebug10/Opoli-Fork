package dev.overgrown.apoli.data;

public final class BodyAttachments {
    private BodyAttachments() {}

    public static final int RIGHT_WING = 1;
    public static final int LEFT_WING = 1 << 1;
    public static final int WING_PAIR = 1 << 2;
    public static final int RIGHT_EAR = 1 << 3;
    public static final int LEFT_EAR = 1 << 4;
    public static final int EAR_PAIR = 1 << 5;
    public static final int HORNS = 1 << 6;
    public static final int SNOUT = 1 << 7;
    public static final int TAIL = 1 << 8;
    public static final int RIGHT_ARM_CLAW = 1 << 9;
    public static final int LEFT_ARM_CLAW = 1 << 10;
    public static final int RIGHT_LEG_CLAW = 1 << 11;
    public static final int LEFT_LEG_CLAW = 1 << 12;
    public static final int CHEST = 1 << 13;
    public static final int CAPE = 1 << 14;
    public static final int COUNT = 15;

    public static final int WINGS = RIGHT_WING | LEFT_WING | WING_PAIR;
    public static final int EARS = RIGHT_EAR | LEFT_EAR | EAR_PAIR;
    public static final int CLAWS = RIGHT_ARM_CLAW | LEFT_ARM_CLAW | RIGHT_LEG_CLAW | LEFT_LEG_CLAW;

    public static int index(int bit) {
        return Integer.numberOfTrailingZeros(bit);
    }
}
