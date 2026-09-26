package dev.overgrown.apoli.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BodyPart {

    public static final int HEAD = 1;
    public static final int HAT = 1 << 1;
    public static final int BODY = 1 << 2;
    public static final int JACKET = 1 << 3;
    public static final int RIGHT_ARM = 1 << 4;
    public static final int RIGHT_SLEEVE = 1 << 5;
    public static final int LEFT_ARM = 1 << 6;
    public static final int LEFT_SLEEVE = 1 << 7;
    public static final int RIGHT_LEG = 1 << 8;
    public static final int RIGHT_PANTS = 1 << 9;
    public static final int LEFT_LEG = 1 << 10;
    public static final int LEFT_PANTS = 1 << 11;
    public static final int ALL_MODELS = (1 << 12) - 1;

    public static final int LIMB_HEAD = 1 << HumanoidPose.HEAD;
    public static final int LIMB_BODY = 1 << HumanoidPose.BODY;
    public static final int LIMB_RIGHT_ARM = 1 << HumanoidPose.RIGHT_ARM;
    public static final int LIMB_LEFT_ARM = 1 << HumanoidPose.LEFT_ARM;
    public static final int LIMB_RIGHT_LEG = 1 << HumanoidPose.RIGHT_LEG;
    public static final int LIMB_LEFT_LEG = 1 << HumanoidPose.LEFT_LEG;
    public static final int ALL_LIMBS = (1 << HumanoidPose.PART_COUNT) - 1;

    static final int POINT_NONE = 0;
    static final int POINT_LIMB = 1;
    static final int POINT_MIDPOINT = 2;
    static final int POINT_FIXED = 3;

    public record Region(int limbs, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Region whole(int limbs) {
            float inf = Float.POSITIVE_INFINITY;
            return new Region(limbs, -inf, -inf, -inf, inf, inf, inf);
        }

        public boolean contains(int limb, double x, double y, double z) {
            return (limbs & (1 << limb)) != 0
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }
    }

    private final String name;
    private final String key;
    private final int models;
    private final int limbs;
    private final boolean group;
    private final boolean everything;
    private final boolean custom;
    private final Region[] regions;
    private final int pointKind;
    private final int pointLimbA;
    private final float pointAX;
    private final float pointAY;
    private final float pointAZ;
    private final int pointLimbB;
    private final float pointBX;
    private final float pointBY;
    private final float pointBZ;
    private final String bindKey;
    private BodyPart rightHanded;
    private BodyPart leftHanded;
    private int attachments;

    BodyPart(String name, String key, int models, int limbs, boolean group, boolean everything, boolean custom,
             Region[] regions, int pointKind, int pointLimbA, float pointAX, float pointAY, float pointAZ,
             int pointLimbB, float pointBX, float pointBY, float pointBZ, @Nullable String bindKey) {
        this.name = name;
        this.key = key;
        this.models = models;
        this.limbs = limbs;
        this.group = group;
        this.everything = everything;
        this.custom = custom;
        this.regions = regions;
        this.pointKind = pointKind;
        this.pointLimbA = pointLimbA;
        this.pointAX = pointAX;
        this.pointAY = pointAY;
        this.pointAZ = pointAZ;
        this.pointLimbB = pointLimbB;
        this.pointBX = pointBX;
        this.pointBY = pointBY;
        this.pointBZ = pointBZ;
        this.bindKey = bindKey;
    }

    void handed(BodyPart rightHanded, BodyPart leftHanded) {
        this.rightHanded = rightHanded;
        this.leftHanded = leftHanded;
    }

    void attach(int attachments) {
        this.attachments = attachments;
    }

    public int attachments() {
        return attachments;
    }

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    public int models() {
        return models;
    }

    public int limbs() {
        return limbs;
    }

    public boolean isGroup() {
        return group;
    }

    public boolean isEverything() {
        return everything;
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean isHanded() {
        return rightHanded != null;
    }

    @Nullable
    public String bindKey() {
        return bindKey;
    }

    public BodyPart sided(@Nullable Entity entity) {
        if (rightHanded == null) return this;
        return entity instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? leftHanded : rightHanded;
    }

    public BodyPart sided(boolean leftHanded) {
        if (rightHanded == null) return this;
        return leftHanded ? this.leftHanded : this.rightHanded;
    }

    public boolean contains(int limb, double x, double y, double z) {
        for (int i = 0; i < regions.length; i++) {
            if (regions[i].contains(limb, x, y, z)) return true;
        }
        return false;
    }

    public boolean hasPoint() {
        return pointKind != POINT_NONE;
    }

    public boolean pointInto(float[] limbPoses, double[] scratch, float[] out) {
        switch (pointKind) {
            case POINT_LIMB -> {
                PoseMath.localToParent(limbPoses, pointLimbA * PoseMath.STRIDE, pointAX, pointAY, pointAZ, scratch);
                out[0] = (float) scratch[0];
                out[1] = (float) scratch[1];
                out[2] = (float) scratch[2];
                return true;
            }
            case POINT_MIDPOINT -> {
                PoseMath.localToParent(limbPoses, pointLimbA * PoseMath.STRIDE, pointAX, pointAY, pointAZ, scratch);
                double x = scratch[0];
                double y = scratch[1];
                double z = scratch[2];
                PoseMath.localToParent(limbPoses, pointLimbB * PoseMath.STRIDE, pointBX, pointBY, pointBZ, scratch);
                out[0] = (float) ((x + scratch[0]) * 0.5);
                out[1] = (float) ((y + scratch[1]) * 0.5);
                out[2] = (float) ((z + scratch[2]) * 0.5);
                return true;
            }
            case POINT_FIXED -> {
                out[0] = pointAX;
                out[1] = pointAY;
                out[2] = pointAZ;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public int pickLimb(RandomSource random) {
        int count = Integer.bitCount(limbs);
        if (count == 0) return HumanoidPose.BODY;
        int skip = count == 1 ? 0 : random.nextInt(count);
        for (int limb = 0; limb < HumanoidPose.PART_COUNT; limb++) {
            if ((limbs & (1 << limb)) == 0) continue;
            if (skip-- == 0) return limb;
        }
        return HumanoidPose.BODY;
    }

    public void centreInto(int limb, float[] out) {
        float minX = HumanoidPose.boxMin(limb, 0);
        float minY = HumanoidPose.boxMin(limb, 1);
        float minZ = HumanoidPose.boxMin(limb, 2);
        float maxX = HumanoidPose.boxMax(limb, 0);
        float maxY = HumanoidPose.boxMax(limb, 1);
        float maxZ = HumanoidPose.boxMax(limb, 2);
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            if ((region.limbs() & (1 << limb)) == 0) continue;
            minX = Math.max(minX, region.minX());
            minY = Math.max(minY, region.minY());
            minZ = Math.max(minZ, region.minZ());
            maxX = Math.min(maxX, region.maxX());
            maxY = Math.min(maxY, region.maxY());
            maxZ = Math.min(maxZ, region.maxZ());
            break;
        }
        out[0] = (minX + maxX) * 0.5F;
        out[1] = (minY + maxY) * 0.5F;
        out[2] = (minZ + maxZ) * 0.5F;
    }

    @Override
    public String toString() {
        return name;
    }

    public static final Codec<BodyPart> CODEC = Codec.STRING.xmap(BodyParts::lookupOrCustom, BodyPart::name);

    public static final Codec<BodyPart> STRICT_CODEC = Codec.STRING.comapFlatMap(BodyParts::strict, BodyPart::name);

    public static final Codec<List<BodyPart>> LIST_CODEC = listOf(CODEC);

    public static final Codec<List<BodyPart>> STRICT_LIST_CODEC = listOf(STRICT_CODEC);

    private static Codec<List<BodyPart>> listOf(Codec<BodyPart> single) {
        Codec<List<BodyPart>> list = single.listOf();
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<List<BodyPart>, T>> decode(DynamicOps<T> ops, T input) {
                if (ops.getStream(input).result().isPresent()) {
                    return list.decode(ops, input);
                }
                return single.decode(ops, input).map(pair -> pair.mapFirst(List::of));
            }

            @Override
            public <T> DataResult<T> encode(List<BodyPart> input, DynamicOps<T> ops, T prefix) {
                return input.size() == 1 ? single.encode(input.get(0), ops, prefix) : list.encode(input, ops, prefix);
            }
        };
    }
}
