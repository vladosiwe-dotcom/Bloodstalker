package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.entity.ai.GorewidowAmbushGoal;
import com.vladosiwe.gorewidow.entity.ai.GorewidowDrainGoal;
import com.vladosiwe.gorewidow.entity.ai.GorewidowMeleeGoal;
import com.vladosiwe.gorewidow.entity.ai.GorewidowPounceGoal;
import com.vladosiwe.gorewidow.entity.ai.GorewidowWebPullGoal;
import com.vladosiwe.gorewidow.entity.ai.GorewidowWebTraverseGoal;
import com.vladosiwe.gorewidow.network.MountActionPayload;
import com.vladosiwe.gorewidow.network.SurfaceSyncPayload;
import com.vladosiwe.gorewidow.registry.ModDamageTypes;
import com.vladosiwe.gorewidow.registry.ModEffects;
import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModItems;
import com.vladosiwe.gorewidow.registry.ModParticles;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The Gorewidow: a long-legged blood predator that crawls on any surface, swings on web lines,
 * reels prey in and drains it. Tamed with blood vials, ridden hanging underneath its belly.
 *
 * <p>Movement authority follows vanilla: whoever "controls" the entity (the server for AI, the
 * rider's client while ridden) runs {@link #travel}. Surface attachment changes made by a riding
 * client are mirrored to the server with {@link SurfaceSyncPayload}; web actions always run on
 * the server and reach every client through synced data.
 */
public class GorewidowEntity extends TamableAnimal implements GeoEntity, PlayerRideableJumping {

    // ------------------------------------------------------------------ synced data
    private static final EntityDataAccessor<Byte> DATA_SURFACE = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WEB_MODE = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> DATA_ANCHOR = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_ANCHOR_FACE = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_LINK_ID = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ROPE_LENGTH = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_HEADING = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_DRAIN_ID = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TAMING = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HARNESS = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_COMMAND = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_PERCHED = SynchedEntityData.defineId(GorewidowEntity.class, EntityDataSerializers.BOOLEAN);

    // ------------------------------------------------------------------ tuning
    public static final double WEB_RANGE = 36.0;
    public static final double WEB_SHOT_SPEED = 2.4;
    private static final double GRAVITY = 0.08;
    private static final double SURFACE_STICK = 0.08;
    private static final float MIN_ROPE = 1.6F;
    private static final float MAX_ROPE = 40.0F;
    private static final int TETHER_MAX_TICKS = 50;
    private static final int DRAIN_TICKS = 70;

    // ------------------------------------------------------------------ animations
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("climb");
    private static final RawAnimation CLING = RawAnimation.begin().thenLoop("cling");
    private static final RawAnimation HANG = RawAnimation.begin().thenLoop("hang");
    private static final RawAnimation SWING = RawAnimation.begin().thenLoop("swing");
    private static final RawAnimation AIR = RawAnimation.begin().thenLoop("air");
    private static final RawAnimation WEB_PULL = RawAnimation.begin().thenLoop("web_pull");
    private static final RawAnimation DRAIN = RawAnimation.begin().thenLoop("drain");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation PERCHED = RawAnimation.begin().thenLoop("lurk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
    private static final RawAnimation WEB_SHOOT = RawAnimation.begin().thenPlay("web_shoot");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
    private static final RawAnimation SCREECH = RawAnimation.begin().thenPlay("screech");
    private static final RawAnimation TAME = RawAnimation.begin().thenPlay("tame");
    private static final RawAnimation MOUNT = RawAnimation.begin().thenPlay("mount");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlay("land");
    private static final RawAnimation LEAP = RawAnimation.begin().thenPlay("leap");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ------------------------------------------------------------------ movement state
    /** Direction from the body toward the surface it clings to. DOWN = standing on the floor. */
    private Direction surface = Direction.DOWN;
    /** Locally simulated rope length (authoritative on the controlling side). */
    private float ropeLength = 4.0F;
    /** World-space movement wish from the AI move control while off the floor. */
    private Vec3 aiMoveDir = Vec3.ZERO;
    /** AI reel input: +1 reels in, -1 pays out. */
    private float aiReel;
    /** Rider reel input (client side, from key bindings): +1 in, -1 out. */
    private float riderReel;
    /** Whether the AI wants to grab surfaces it bumps into while swinging. */
    private boolean aiGrabSurfaces = true;
    private float pendingRiderJump;
    private int surfaceSyncCooldown;
    private boolean wasOnGroundLastTick = true;
    private float peakFallSpeed;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    /** Client-side visual speed (blocks/tick) used to pick animations. */
    private float clientSpeed;

    // ------------------------------------------------------------------ combat / web state (server)
    private int webCooldown;
    private int tetherTicks;
    @Nullable
    private BlockPos anchorBlock;
    private int ropeTicks;
    private int drainTicks;
    private float damageTakenWhileDraining;
    private boolean pounceStrike;
    /** Web shot purpose so goals can react when it lands. */
    private WebPurpose webPurpose = WebPurpose.ATTACK;

    // ------------------------------------------------------------------ taming
    private int pacifiedTicks;
    @Nullable
    private UUID tamerId;
    private int ticksSinceFed;

    public enum WebPurpose { ATTACK, TRAVERSE, PERCH, RIDER }

    public GorewidowEntity(EntityType<? extends GorewidowEntity> type, Level level) {
        super(type, level);
        this.moveControl = new GorewidowMoveControl(this);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 10.0)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.4);
    }

    // ================================================================== data
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SURFACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(DATA_WEB_MODE, (byte) WebMode.NONE.ordinal());
        builder.define(DATA_ANCHOR, new Vector3f());
        builder.define(DATA_ANCHOR_FACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(DATA_LINK_ID, -1);
        builder.define(DATA_ROPE_LENGTH, 4.0F);
        builder.define(DATA_HEADING, new Vector3f(0, 0, 1));
        builder.define(DATA_DRAIN_ID, -1);
        builder.define(DATA_TAMING, 0);
        builder.define(DATA_HARNESS, false);
        builder.define(DATA_COMMAND, (byte) GorewidowCommand.FOLLOW.ordinal());
        builder.define(DATA_PERCHED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide) {
            if (DATA_SURFACE.equals(key) && !this.isControlledByLocalInstance()) {
                this.surface = Direction.from3DDataValue(this.entityData.get(DATA_SURFACE));
            } else if (DATA_ROPE_LENGTH.equals(key) && !this.isControlledByLocalInstance()) {
                this.ropeLength = this.entityData.get(DATA_ROPE_LENGTH);
            } else if (DATA_WEB_MODE.equals(key)) {
                // a fresh line always starts from the server's measured length
                this.ropeLength = this.entityData.get(DATA_ROPE_LENGTH);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TamingProgress", this.getTamingProgress());
        tag.putBoolean("Harness", this.hasHarness());
        tag.putByte("Command", (byte) this.getCommand().ordinal());
        if (this.tamerId != null) {
            tag.putUUID("Tamer", this.tamerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_TAMING, tag.getInt("TamingProgress"));
        this.entityData.set(DATA_HARNESS, tag.getBoolean("Harness"));
        this.setCommand(GorewidowCommand.byId(tag.getByte("Command")));
        this.tamerId = tag.hasUUID("Tamer") ? tag.getUUID("Tamer") : null;
    }

    // ================================================================== goals
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new GorewidowDrainGoal(this));
        this.goalSelector.addGoal(3, new GorewidowPounceGoal(this));
        this.goalSelector.addGoal(4, new GorewidowWebPullGoal(this));
        this.goalSelector.addGoal(5, new GorewidowWebTraverseGoal(this));
        this.goalSelector.addGoal(6, new GorewidowMeleeGoal(this));
        this.goalSelector.addGoal(7, new FollowOwnerGoal(this, 1.2, 12.0F, 4.0F) {
            @Override
            public boolean canUse() {
                return GorewidowEntity.this.getCommand() == GorewidowCommand.FOLLOW && super.canUse();
            }
        });
        this.goalSelector.addGoal(8, new GorewidowAmbushGoal(this));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return (!GorewidowEntity.this.isTame() || GorewidowEntity.this.getCommand() == GorewidowCommand.WANDER)
                        && !GorewidowEntity.this.isPerched() && super.canUse();
            }
        });
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isHuntablePlayer));
        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false,
                prey -> !(prey instanceof GorewidowEntity) && prey.getBbWidth() < 2.0F));
    }

    /** Removes every goal so physics can be exercised deterministically (game tests). */
    public void clearGoalsForTesting() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
    }

    private boolean isHuntablePlayer(LivingEntity entity) {
        if (this.isTame() || this.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return !(this.isPacified() && entity.getUUID().equals(this.tamerId));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    // ================================================================== accessors
    public Direction getSurface() {
        return this.surface;
    }

    public boolean isOnFloor() {
        return this.surface == Direction.DOWN;
    }

    /** Unit vector pointing away from the surface the body clings to. */
    public Vec3 getSurfaceNormal() {
        return Vec3.atLowerCornerOf(this.surface.getOpposite().getNormal());
    }

    public WebMode getWebMode() {
        return WebMode.byId(this.entityData.get(DATA_WEB_MODE));
    }

    public Vec3 getAnchor() {
        Vector3f v = this.entityData.get(DATA_ANCHOR);
        return new Vec3(v.x, v.y, v.z);
    }

    public Direction getAnchorFace() {
        return Direction.from3DDataValue(this.entityData.get(DATA_ANCHOR_FACE));
    }

    public float getRopeLength() {
        return this.ropeLength;
    }

    @Nullable
    public Entity getLinkedEntity() {
        int id = this.entityData.get(DATA_LINK_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Nullable
    public LivingEntity getDrainTarget() {
        int id = this.entityData.get(DATA_DRAIN_ID);
        return id >= 0 && this.level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public boolean isDraining() {
        return this.entityData.get(DATA_DRAIN_ID) >= 0;
    }

    public int getTamingProgress() {
        return this.entityData.get(DATA_TAMING);
    }

    public boolean hasHarness() {
        return this.entityData.get(DATA_HARNESS);
    }

    public void setHarness(boolean harness) {
        this.entityData.set(DATA_HARNESS, harness);
    }

    public GorewidowCommand getCommand() {
        return GorewidowCommand.byId(this.entityData.get(DATA_COMMAND));
    }

    public void setCommand(GorewidowCommand command) {
        this.entityData.set(DATA_COMMAND, (byte) command.ordinal());
        this.setOrderedToSit(command == GorewidowCommand.STAY);
        this.setInSittingPose(command == GorewidowCommand.STAY);
    }

    public boolean isPerched() {
        return this.entityData.get(DATA_PERCHED);
    }

    public void setPerched(boolean perched) {
        this.entityData.set(DATA_PERCHED, perched);
    }

    public boolean isPacified() {
        return this.pacifiedTicks > 0;
    }

    public int getWebCooldown() {
        return this.webCooldown;
    }

    public void setWebCooldown(int ticks) {
        this.webCooldown = ticks;
    }

    public WebPurpose getWebPurpose() {
        return this.webPurpose;
    }

    public void setAiMoveDir(Vec3 dir) {
        this.aiMoveDir = dir;
    }

    public void setAiReel(float reel) {
        this.aiReel = reel;
    }

    public void setAiGrabSurfaces(boolean grab) {
        this.aiGrabSurfaces = grab;
    }

    public void setRiderReel(float reel) {
        this.riderReel = reel;
    }

    public int getRopeTicks() {
        return this.ropeTicks;
    }

    public float getClientSpeed() {
        return this.clientSpeed;
    }

    public boolean isPounceStrike() {
        return this.pounceStrike;
    }

    public void setPounceStrike(boolean pounce) {
        this.pounceStrike = pounce;
    }

    /** Rope attachment point on the body (bounding box centre). */
    public Vec3 getBodyCenter() {
        return this.position().add(0, this.getBbHeight() * 0.5, 0);
    }

    // ================================================================== body frame
    /**
     * Unit "up" of the body frame (away from the belly). On surfaces it is the surface normal; on a
     * line the body hangs with its abdomen toward the anchor.
     */
    public Vec3 getBodyUp() {
        WebMode mode = this.getWebMode();
        if (mode.isRope() && !this.onGround()) {
            Vec3 f = this.getBodyForward();
            Vec3 ref = Math.abs(f.y) < 0.85 ? new Vec3(0, 1, 0) : this.getHeadingVec();
            Vec3 up = ref.subtract(f.scale(ref.dot(f)));
            return up.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : up.normalize();
        }
        return this.getSurfaceNormal();
    }

    /** Unit forward of the body frame (where the head points). */
    public Vec3 getBodyForward() {
        WebMode mode = this.getWebMode();
        if (mode.isRope() && !this.onGround()) {
            Vec3 away = this.getBodyCenter().subtract(this.getAnchor());
            if (away.lengthSqr() > 1.0E-4) {
                return away.normalize();
            }
        }
        if (this.surface == Direction.DOWN) {
            float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        }
        Vec3 n = this.getSurfaceNormal();
        Vec3 h = this.getHeadingVec();
        Vec3 t = h.subtract(n.scale(h.dot(n)));
        if (t.lengthSqr() < 1.0E-4) {
            t = this.surface.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        }
        return t.normalize();
    }

    /** Heading on the current surface: from the rider's look when ridden, otherwise synced from the server. */
    public Vec3 getHeadingVec() {
        if (this.getControllingPassenger() instanceof Player rider) {
            return rider.getLookAngle();
        }
        Vector3f v = this.entityData.get(DATA_HEADING);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setHeading(Vec3 dir) {
        if (!this.level().isClientSide && dir.lengthSqr() > 1.0E-6) {
            Vec3 n = dir.normalize();
            Vector3f old = this.entityData.get(DATA_HEADING);
            if (old.distanceSquared((float) n.x, (float) n.y, (float) n.z) > 0.004F) {
                this.entityData.set(DATA_HEADING, new Vector3f((float) n.x, (float) n.y, (float) n.z));
            }
        }
    }

    /** Model-space point (GeckoLib internal px, see {@link GorewidowAnatomy}) to world space using the logical body frame. */
    public Vec3 modelToWorld(float x, float y, float z) {
        Vec3 up = this.getBodyUp();
        Vec3 fwd = this.getBodyForward();
        Vec3 side = fwd.cross(up);
        Vec3 origin = this.getBodyCenter().subtract(up.scale(this.getBbHeight() * 0.5));
        return origin.add(side.scale(x / 16.0)).add(up.scale(y / 16.0)).add(fwd.scale(-z / 16.0));
    }

    public Vec3 getSpinneretPos() {
        float[] s = GorewidowAnatomy.SPINNERET;
        return this.modelToWorld(s[0], s[1], s[2]);
    }

    public Vec3 getMouthPos() {
        float[] m = GorewidowAnatomy.MOUTH;
        return this.modelToWorld(m[0], m[1], m[2]);
    }

    // ================================================================== surface handling
    private boolean collidesToward(Direction dir, double distance) {
        return this.collidesToward(this.getBoundingBox(), dir, distance);
    }

    private boolean collidesToward(AABB box, Direction dir, double distance) {
        Vec3 d = Vec3.atLowerCornerOf(dir.getNormal()).scale(distance);
        return !this.level().noCollision(this, box.move(d));
    }

    public boolean hasContact(Direction dir) {
        return this.collidesToward(dir, 0.12);
    }

    /** Changes the clinging surface and keeps every side informed. */
    public void setSurface(Direction newSurface) {
        if (newSurface == this.surface) {
            return;
        }
        this.surface = newSurface;
        boolean attached = newSurface != Direction.DOWN;
        if (attached) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.4));
            this.resetFallDistance();
            this.setPerched(false);
        }
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_SURFACE, (byte) newSurface.get3DDataValue());
            this.setNoGravity(attached || this.getWebMode().isRope());
        } else if (this.isControlledByLocalInstance()) {
            PacketDistributor.sendToServer(new SurfaceSyncPayload(this.getId(), (byte) newSurface.get3DDataValue()));
        }
    }

    /** Server-side handling of a riding client's surface report; rejects claims without real contact. */
    public void acceptClientSurface(Direction claimed) {
        if (claimed == Direction.DOWN || this.collidesToward(claimed, 0.6)) {
            this.surface = claimed;
            this.entityData.set(DATA_SURFACE, (byte) claimed.get3DDataValue());
            this.setNoGravity(claimed != Direction.DOWN || this.getWebMode().isRope());
            this.resetFallDistance();
        }
    }

    @Nullable
    private Direction findContact(Vec3 preferred, @Nullable Direction exclude) {
        Direction best = null;
        double bestScore = -10;
        for (Direction d : Direction.values()) {
            if (d == exclude || !this.hasContact(d)) {
                continue;
            }
            double score = Vec3.atLowerCornerOf(d.getNormal()).dot(preferred) + (d == Direction.DOWN ? 0.25 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /** Desired world-space movement (magnitude 0..1) for whoever is steering. */
    private Vec3 desiredDirection(Vec3 input, Vec3 planeNormal) {
        if (this.getControllingPassenger() instanceof Player rider) {
            Vec3 look = rider.getLookAngle();
            Vec3 f = look.subtract(planeNormal.scale(look.dot(planeNormal)));
            if (f.lengthSqr() < 0.04) {
                float yaw = rider.getYRot() * Mth.DEG_TO_RAD;
                Vec3 flat = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                f = flat.subtract(planeNormal.scale(flat.dot(planeNormal)));
                if (f.lengthSqr() < 0.04) {
                    f = new Vec3(0, 1, 0).subtract(planeNormal.scale(planeNormal.y));
                }
            }
            f = f.normalize();
            Vec3 left = planeNormal.cross(f);
            return f.scale(input.z).add(left.scale(input.x));
        }
        return this.aiMoveDir;
    }

    private double surfaceSpeed() {
        double base = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return switch (this.surface) {
            case UP -> base * 0.8;
            case DOWN -> base;
            default -> base * 0.9;
        };
    }

    // ================================================================== travel
    @Override
    public void travel(Vec3 input) {
        if (!this.isControlledByLocalInstance() || this.isDeadOrDying()) {
            if (this.isDeadOrDying() && this.surface != Direction.DOWN && !this.level().isClientSide) {
                this.setSurface(Direction.DOWN);
            }
            super.travel(this.isDeadOrDying() ? Vec3.ZERO : input);
            return;
        }
        WebMode mode = this.getWebMode();
        if (mode.isRope()) {
            this.travelRope(input, mode);
        } else if (this.surface != Direction.DOWN) {
            this.travelSurface(input);
        } else {
            this.travelGround(input);
        }
        this.calculateEntityAnimation(true);
    }

    private void travelGround(Vec3 input) {
        Vec3 before = this.position();
        super.travel(input);
        if (!this.horizontalCollision || this.isInWater() || this.isPassenger()) {
            this.stuckTicks = 0;
            return;
        }
        Vec3 moved = this.position().subtract(before);
        if (moved.horizontalDistanceSqr() < 0.0025) {
            this.stuckTicks++;
        }
        Vec3 wish;
        boolean climb;
        if (this.getControllingPassenger() instanceof Player rider) {
            float yaw = this.getYRot() * Mth.DEG_TO_RAD;
            wish = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            climb = input.z > 0.1;
        } else {
            wish = new Vec3(this.aiMoveDir.x, 0, this.aiMoveDir.z);
            climb = this.aiMoveDir.y > 0.15 || this.stuckTicks > 6;
        }
        if (climb && wish.lengthSqr() > 1.0E-4) {
            Direction wall = null;
            double best = 0.3;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                double dot = Vec3.atLowerCornerOf(d.getNormal()).dot(wish.normalize());
                if (dot > best && this.hasContact(d)) {
                    best = dot;
                    wall = d;
                }
            }
            if (wall != null) {
                this.setSurface(wall);
                this.stuckTicks = 0;
                this.setDeltaMovement(0, 0.15, 0);
            }
        }
    }

    private void travelSurface(Vec3 input) {
        Vec3 n = this.getSurfaceNormal();
        Vec3 desired = this.desiredDirection(input, n);
        Vec3 t = desired.subtract(n.scale(desired.dot(n)));
        double tl = t.length();
        double speed = this.surfaceSpeed();
        Vec3 targetVel = tl > 1.0E-4 ? t.scale(speed * Math.min(1.0, tl) / tl) : Vec3.ZERO;
        Vec3 v = this.getDeltaMovement();
        Vec3 vt = v.subtract(n.scale(v.dot(n))).lerp(targetVel, 0.45);
        Vec3 motion = vt.add(n.scale(-SURFACE_STICK));
        Vec3 start = this.position();
        this.move(MoverType.SELF, motion);
        Vec3 moved = this.position().subtract(start);
        this.setDeltaMovement(vt);
        this.resetFallDistance();
        if (vt.lengthSqr() > 1.0E-4) {
            this.setHeading(vt);
        }

        // concave edge: blocked by another surface in the direction we're heading
        if (tl > 0.05) {
            Vec3 tn = t.normalize();
            Direction blocked = null;
            double bestDot = 0.4;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == this.surface.getAxis()) {
                    continue;
                }
                Vec3 dv = Vec3.atLowerCornerOf(d.getNormal());
                double dot = tn.dot(dv);
                if (dot > bestDot && moved.dot(dv) < targetVel.dot(dv) * 0.4 && this.hasContact(d)) {
                    bestDot = dot;
                    blocked = d;
                }
            }
            if (blocked != null) {
                this.setSurface(blocked);
                return;
            }
        }

        if (!this.hasContact(this.surface)) {
            // convex edge: wrap around the corner we just walked off
            Vec3 toward = Vec3.atLowerCornerOf(this.surface.getNormal());
            if (tl > 0.05) {
                Direction wrap = Direction.getNearest(-t.x, -t.y, -t.z);
                AABB shifted = this.getBoundingBox().move(toward.scale(0.6));
                if (wrap.getAxis() != this.surface.getAxis() && this.level().noCollision(this, shifted) && this.collidesToward(shifted, wrap, 0.15)) {
                    this.setPos(this.position().add(toward.scale(0.6)));
                    this.setSurface(wrap);
                    return;
                }
            }
            Direction other = this.findContact(desired, this.surface.getOpposite());
            this.setSurface(other != null ? other : Direction.DOWN);
        }
    }

    private void travelRope(Vec3 input, WebMode mode) {
        Vec3 anchor = this.getAnchor();
        Vec3 c = this.getBodyCenter();
        Vec3 r = c.subtract(anchor);
        double dist = r.length();
        Vec3 rn = dist > 1.0E-4 ? r.scale(1.0 / dist) : new Vec3(0, -1, 0);

        if (this.onGround() && dist < this.ropeLength - 0.25) {
            // slack line while walking: plain ground movement, but never past the line's length
            this.travelGround(input);
            this.enforceRope(anchor);
            this.ropeTicks++;
            return;
        }

        float reel = this.getControllingPassenger() instanceof Player ? this.riderReel : this.aiReel;
        float reelSpeed = mode == WebMode.HANG ? 0.12F : 0.28F;
        this.ropeLength = Mth.clamp(this.ropeLength - reel * reelSpeed, MIN_ROPE, MAX_ROPE);

        Vec3 v = this.getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 desired = this.desiredDirection(input, rn);
        Vec3 tangential = desired.subtract(rn.scale(desired.dot(rn)));
        v = v.add(tangential.scale(mode == WebMode.HANG ? 0.01 : 0.04));
        v = v.scale(mode == WebMode.HANG ? 0.9 : 0.988);

        Vec3 next = c.add(v);
        Vec3 d = next.subtract(anchor);
        double nd = d.length();
        Vec3 delta;
        if (nd > this.ropeLength) {
            Vec3 dn = d.scale(1.0 / nd);
            double radial = v.dot(dn);
            if (radial > 0) {
                v = v.subtract(dn.scale(radial));
            }
            delta = anchor.add(dn.scale(this.ropeLength)).subtract(c);
            if (delta.lengthSqr() > 4.0) {
                delta = delta.normalize().scale(2.0);
            }
        } else {
            delta = v;
        }
        this.move(MoverType.SELF, delta);
        if (this.horizontalCollision) {
            v = new Vec3(v.x * 0.25, v.y, v.z * 0.25);
        }
        if (this.verticalCollision) {
            v = new Vec3(v.x * 0.7, 0, v.z * 0.7);
        }
        this.setDeltaMovement(v);
        this.enforceRope(anchor);
        this.resetFallDistance();
        this.ropeTicks++;
        if (v.lengthSqr() > 1.0E-4) {
            this.setHeading(v);
        }

        boolean wantsGrab = this.getControllingPassenger() instanceof Player ? input.z > 0.1 : this.aiGrabSurfaces;
        boolean nearAnchor = c.distanceTo(anchor) < 2.4;
        if (nearAnchor && reel > 0) {
            Direction face = this.getAnchorFace().getOpposite();
            if (this.collidesToward(face, 1.0)) {
                this.landOnSurfaceFromRope(face);
                return;
            }
        }
        if (wantsGrab && this.ropeTicks > 5 && (this.horizontalCollision || (this.verticalCollision && !this.onGround()))) {
            // only grab what we swing *into*, never the ground we just pushed off
            Vec3 heading = delta.lengthSqr() > 1.0E-6 ? delta.normalize() : rn.reverse();
            Direction contact = this.findContact(heading, Direction.DOWN);
            if (contact != null && Vec3.atLowerCornerOf(contact.getNormal()).dot(heading) > 0.2) {
                this.landOnSurfaceFromRope(contact);
            }
        }
    }

    /** Hard length constraint: pulls the body back onto the rope sphere and kills outward speed. */
    private void enforceRope(Vec3 anchor) {
        Vec3 r = this.getBodyCenter().subtract(anchor);
        double dist = r.length();
        if (dist <= this.ropeLength || dist < 1.0E-4) {
            return;
        }
        Vec3 rn = r.scale(1.0 / dist);
        this.move(MoverType.SELF, rn.scale(-(dist - this.ropeLength)));
        Vec3 v = this.getDeltaMovement();
        double radial = v.dot(rn);
        if (radial > 0) {
            this.setDeltaMovement(v.subtract(rn.scale(radial)));
        }
    }

    private void landOnSurfaceFromRope(Direction contact) {
        // walk the body flush against the surface first
        this.move(MoverType.SELF, Vec3.atLowerCornerOf(contact.getNormal()).scale(0.9));
        this.setSurface(contact);
        if (this.level().isClientSide) {
            if (this.isControlledByLocalInstance()) {
                this.entityData.set(DATA_WEB_MODE, (byte) WebMode.NONE.ordinal());
                PacketDistributor.sendToServer(new MountActionPayload(MountActionPayload.Action.RELEASE_WEB));
            }
        } else {
            this.releaseWeb(false);
        }
    }

    // ================================================================== tick
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientSpeed = (float) this.position().distanceTo(new Vec3(this.xo, this.yo, this.zo));
            if (!this.isControlledByLocalInstance()) {
                this.surface = Direction.from3DDataValue(this.entityData.get(DATA_SURFACE));
            }
            this.clientEffects();
            return;
        }
        this.serverTick();
    }

    private void serverTick() {
        if (this.webCooldown > 0) {
            this.webCooldown--;
        }
        if (this.pacifiedTicks > 0 && --this.pacifiedTicks == 0) {
            this.tamerId = null;
        }
        this.tickTamingDecay();

        WebMode mode = this.getWebMode();
        if (mode != WebMode.NONE && this.isDeadOrDying()) {
            this.releaseWeb(false);
            mode = WebMode.NONE;
        }
        switch (mode) {
            case SHOT -> {
                Entity shot = this.getLinkedEntity();
                if (!(shot instanceof WebShotEntity) || !shot.isAlive()) {
                    this.releaseWeb(false);
                }
            }
            case SWING, HANG -> this.tickRopeServer();
            case TETHER -> this.tickTether();
            default -> {
            }
        }
        if (!mode.isRope()) {
            this.ropeTicks = 0;
        }
        this.setNoGravity(this.surface != Direction.DOWN || this.getWebMode().isRope());

        // keep the surface flag honest while the server is in control
        if (this.isControlledByLocalInstance() && this.surface != Direction.DOWN && !this.hasContact(this.surface)
                && !this.collidesToward(this.surface, 0.5)) {
            this.setSurface(Direction.DOWN);
        }
        if (this.getControllingPassenger() instanceof Player && this.surface != Direction.DOWN && !this.collidesToward(this.surface, 0.8)) {
            // the rider's client claimed a surface we no longer touch
            this.surface = Direction.DOWN;
            this.entityData.set(DATA_SURFACE, (byte) Direction.DOWN.get3DDataValue());
        }

        this.tickDrain();
        this.tickLanding();
    }

    private void tickLanding() {
        boolean ground = this.onGround();
        if (!ground) {
            this.peakFallSpeed = (float) Math.max(this.peakFallSpeed, -this.getDeltaMovement().y);
        } else if (!this.wasOnGroundLastTick && this.peakFallSpeed > 0.45F && this.surface == Direction.DOWN) {
            this.triggerAnim("action", "land");
            this.playSound(ModSounds.LAND.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            if (this.level() instanceof ServerLevel server) {
                BlockState below = this.getBlockStateOn();
                if (!below.isAir()) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX(), this.getY() + 0.1, this.getZ(),
                            30, this.getBbWidth() * 0.6, 0.05, this.getBbWidth() * 0.6, 0.15);
                }
                server.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.8, 0.05, 0.8, 0.02);
            }
            this.peakFallSpeed = 0;
        }
        if (ground) {
            this.peakFallSpeed = 0;
        }
        this.wasOnGroundLastTick = ground;
    }

    private void tickRopeServer() {
        if (this.anchorBlock != null && this.level().getBlockState(this.anchorBlock).getCollisionShape(this.level(), this.anchorBlock).isEmpty()) {
            this.releaseWeb(true);
            return;
        }
        if (this.getBodyCenter().distanceTo(this.getAnchor()) > MAX_ROPE + 6) {
            this.releaseWeb(true);
            return;
        }
        if (!(this.getControllingPassenger() instanceof Player)) {
            this.entityData.set(DATA_ROPE_LENGTH, this.ropeLength);
            if (this.ropeTicks % 25 == 0 && this.getDeltaMovement().lengthSqr() > 0.2) {
                this.playSound(ModSounds.WEB_STRAIN.get(), 0.5F, 0.9F + this.random.nextFloat() * 0.3F);
            }
        }
    }

    private void tickTether() {
        Entity linked = this.getLinkedEntity();
        if (!(linked instanceof LivingEntity target) || !target.isAlive() || ++this.tetherTicks > TETHER_MAX_TICKS
                || target.distanceTo(this) > WEB_RANGE + 4) {
            this.releaseWeb(true);
            return;
        }
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 to = this.getBodyCenter();
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 2.6) {
            this.releaseWeb(false);
            if (!(this.getControllingPassenger() instanceof Player)) {
                this.doHurtTarget(target);
            }
            return;
        }
        dir = dir.scale(1.0 / dist);
        double heaviness = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
        double ours = this.getBbWidth() * this.getBbWidth() * this.getBbHeight();
        if (heaviness > ours * 1.5 || target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.9) {
            // too heavy to reel in: haul ourselves to it instead
            this.setSurface(Direction.DOWN);
            Vec3 pull = dir.reverse().scale(0.45).add(0, 0.12, 0);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(pull));
            this.hasImpulse = true;
        } else {
            double strength = 0.34 * (1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6);
            Vec3 pull = dir.scale(strength).add(0, target.onGround() ? 0.08 : 0.02, 0);
            target.setDeltaMovement(target.getDeltaMovement().scale(0.55).add(pull));
            target.hurtMarked = true;
            target.hasImpulse = true;
            target.resetFallDistance();
        }
        if (this.tetherTicks % 8 == 0) {
            this.playSound(ModSounds.WEB_STRAIN.get(), 0.7F, 1.1F + this.random.nextFloat() * 0.2F);
        }
        this.getLookControl().setLookAt(target, 30, 30);
    }

    private void clientEffects() {
        LivingEntity drained = this.getDrainTarget();
        if (drained != null && this.tickCount % 2 == 0) {
            // blood pulled from the victim toward the fangs
            Vec3 mouth = this.getMouthPos();
            for (int i = 0; i < 3; i++) {
                Vec3 src = drained.position().add(
                        (this.random.nextDouble() - 0.5) * drained.getBbWidth(),
                        drained.getBbHeight() * (0.3 + this.random.nextDouble() * 0.5),
                        (this.random.nextDouble() - 0.5) * drained.getBbWidth());
                Vec3 vel = mouth.subtract(src).scale(1.0 / 10.0);
                this.level().addParticle(ModParticles.BLOOD_STREAM.get(), src.x, src.y, src.z, vel.x, vel.y, vel.z);
            }
        }
        if (this.getWebMode() == WebMode.TETHER && this.tickCount % 3 == 0) {
            Entity linked = this.getLinkedEntity();
            if (linked != null) {
                Vec3 p = linked.position().add(0, linked.getBbHeight() * 0.5, 0);
                this.level().addParticle(ModParticles.WEB_FIBER.get(), p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    // ================================================================== web actions (server)
    /** Fires a web line at a world point. Returns false if on cooldown or already busy. */
    public boolean shootWeb(Vec3 aimPoint, WebPurpose purpose) {
        if (this.level().isClientSide || this.isDeadOrDying()) {
            return false;
        }
        if (this.getWebMode() != WebMode.NONE) {
            this.releaseWeb(false);
        }
        // The glob leaves the body toward the target (the line itself is drawn from the spinneret
        // bone), so a Gorewidow with its back to a wall never fires into that wall.
        Vec3 center = this.getBodyCenter();
        Vec3 dir = aimPoint.subtract(center);
        if (dir.lengthSqr() < 1.0E-4) {
            return false;
        }
        Vec3 from = center.add(dir.normalize().scale(1.1));
        if (this.level().clip(new ClipContext(center, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
            from = center;
        }
        dir = aimPoint.subtract(from);
        WebShotEntity shot = new WebShotEntity(this.level(), this, from, dir.normalize().scale(WEB_SHOT_SPEED));
        this.level().addFreshEntity(shot);
        this.webPurpose = purpose;
        this.entityData.set(DATA_LINK_ID, shot.getId());
        this.entityData.set(DATA_WEB_MODE, (byte) WebMode.SHOT.ordinal());
        this.triggerAnim("action", "web_shoot");
        this.playSound(ModSounds.WEB_SHOOT.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        this.setPerched(false);
        return true;
    }

    /** Called by the web shot when it sticks to a block. */
    public void onWebAnchored(Vec3 point, Direction face, BlockPos block) {
        if (this.getWebMode() != WebMode.SHOT) {
            return;
        }
        this.anchorBlock = block;
        this.entityData.set(DATA_ANCHOR, new Vector3f((float) point.x, (float) point.y, (float) point.z));
        this.entityData.set(DATA_ANCHOR_FACE, (byte) face.get3DDataValue());
        this.entityData.set(DATA_LINK_ID, -1);
        float len = (float) Mth.clamp(this.getBodyCenter().distanceTo(point) - 0.1, MIN_ROPE, MAX_ROPE);
        this.ropeLength = len;
        this.entityData.set(DATA_ROPE_LENGTH, len);
        this.ropeTicks = 0;
        this.entityData.set(DATA_WEB_MODE, (byte) WebMode.SWING.ordinal());
        this.setNoGravity(true);
        this.playSound(ModSounds.WEB_HIT.get(), 1.0F, 1.0F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.WEB_FIBER.get(), point.x, point.y, point.z, 14, 0.25, 0.25, 0.25, 0.04);
        }
    }

    /** Called by the web shot when it sticks to a creature. */
    public void onWebTethered(LivingEntity target) {
        if (this.getWebMode() != WebMode.SHOT) {
            return;
        }
        this.entityData.set(DATA_LINK_ID, target.getId());
        this.entityData.set(DATA_WEB_MODE, (byte) WebMode.TETHER.ordinal());
        this.tetherTicks = 0;
        target.addEffect(new MobEffectInstance(ModEffects.WEBBED, 100, 0), this);
        this.playSound(ModSounds.WEB_HIT.get(), 1.0F, 1.2F);
        if (this.getTarget() == null && !(this.getControllingPassenger() instanceof Player) && this.canAttack(target)) {
            this.setTarget(target);
        }
        if (this.level() instanceof ServerLevel server) {
            Vec3 p = target.position().add(0, target.getBbHeight() * 0.5, 0);
            server.sendParticles(ModParticles.WEB_FIBER.get(), p.x, p.y, p.z, 18, 0.3, 0.4, 0.3, 0.05);
        }
    }

    public void onWebMissed() {
        if (this.getWebMode() == WebMode.SHOT) {
            this.releaseWeb(true);
        }
    }

    /** Switches an anchored line into a still, dangling hang (ambush). */
    public void startHang(Vec3 anchor, Direction face, float length) {
        this.anchorBlock = BlockPos.containing(anchor.add(Vec3.atLowerCornerOf(face.getNormal()).scale(-0.1)));
        this.entityData.set(DATA_ANCHOR, new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z));
        this.entityData.set(DATA_ANCHOR_FACE, (byte) face.get3DDataValue());
        this.entityData.set(DATA_LINK_ID, -1);
        this.ropeLength = Math.max(MIN_ROPE, (float) this.getBodyCenter().distanceTo(anchor));
        this.entityData.set(DATA_ROPE_LENGTH, this.ropeLength);
        this.entityData.set(DATA_WEB_MODE, (byte) WebMode.HANG.ordinal());
        this.setSurface(Direction.DOWN);
        this.setNoGravity(true);
    }

    public void releaseWeb(boolean playSound) {
        if (this.getWebMode() == WebMode.NONE) {
            return;
        }
        Entity linked = this.getLinkedEntity();
        if (linked instanceof WebShotEntity shot && !this.level().isClientSide) {
            shot.discard();
        }
        this.entityData.set(DATA_WEB_MODE, (byte) WebMode.NONE.ordinal());
        this.entityData.set(DATA_LINK_ID, -1);
        this.anchorBlock = null;
        this.ropeTicks = 0;
        this.setNoGravity(this.surface != Direction.DOWN);
        if (playSound) {
            this.playSound(ModSounds.WEB_RETRACT.get(), 0.8F, 1.0F);
        }
    }

    // ================================================================== drain
    public boolean canStartDrain(LivingEntity target) {
        return !this.isDraining() && target.isAlive() && !(target instanceof GorewidowEntity)
                && target.getBbHeight() < 3.0F && !this.isAlliedTo(target);
    }

    public void startDrain(LivingEntity target) {
        if (!this.canStartDrain(target)) {
            return;
        }
        this.entityData.set(DATA_DRAIN_ID, target.getId());
        this.drainTicks = 0;
        this.damageTakenWhileDraining = 0;
        this.playSound(ModSounds.SCREECH.get(), 1.0F, 1.3F);
    }

    public void stopDrain() {
        if (this.isDraining()) {
            this.entityData.set(DATA_DRAIN_ID, -1);
        }
    }

    private void tickDrain() {
        LivingEntity target = this.getDrainTarget();
        if (target == null) {
            if (this.isDraining()) {
                this.stopDrain();
            }
            return;
        }
        if (!target.isAlive() || ++this.drainTicks > DRAIN_TICKS || target.distanceTo(this) > 4.5
                || this.damageTakenWhileDraining >= 8.0F) {
            this.stopDrain();
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.WEBBED, 30, 1), this);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
        this.getLookControl().setLookAt(target, 60, 60);
        if (this.drainTicks % 10 == 0) {
            float amount = 2.0F;
            if (target.hurt(ModDamageTypes.bloodDrain(this), amount)) {
                this.heal(amount * 1.5F);
                if (this.getControllingPassenger() instanceof Player rider) {
                    rider.getFoodData().eat(1, 0.3F);
                }
                if (this.level() instanceof ServerLevel server) {
                    Vec3 p = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    server.sendParticles(ModParticles.BLOOD_DROP.get(), p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
        if (this.drainTicks % 20 == 1) {
            this.playSound(ModSounds.DRAIN.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    // ================================================================== combat
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.triggerAnim("action", "bite");
            this.playSound(ModSounds.BITE.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            if (this.level() instanceof ServerLevel server) {
                Vec3 p = target.position().add(0, target.getBbHeight() * 0.6, 0);
                server.sendParticles(ModParticles.IMPACT.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                server.sendParticles(ModParticles.BLOOD_DROP.get(), p.x, p.y, p.z, 10, 0.25, 0.25, 0.25, 0.15);
            }
            if (target instanceof LivingEntity living && !(this.getControllingPassenger() instanceof Player)) {
                boolean helpless = living.hasEffect(ModEffects.WEBBED) || living.getHealth() < living.getMaxHealth() * 0.45F;
                if ((this.pounceStrike || helpless || this.random.nextFloat() < 0.2F) && this.canStartDrain(living)) {
                    this.startDrain(living);
                }
            }
            this.pounceStrike = false;
        }
        return hit;
    }

    /** A rider-commanded bite at whatever the rider is looking at. */
    public void riderBite(Player rider) {
        if (this.isDraining()) {
            this.stopDrain();
            return;
        }
        LivingEntity best = null;
        double bestDist = 4.5 * 4.5;
        Vec3 eye = rider.getEyePosition();
        Vec3 look = rider.getLookAngle();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0),
                e -> e != this && e != rider && e.isAlive() && !this.isAlliedTo(e) && !e.isPassengerOfSameVehicle(rider))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = this.distanceToSqr(e);
            if (d < bestDist && to.normalize().dot(look) > 0.6) {
                bestDist = d;
                best = e;
            }
        }
        this.triggerAnim("action", "bite");
        if (best == null) {
            this.playSound(ModSounds.BITE.get(), 0.6F, 1.3F);
            return;
        }
        if (this.doHurtTarget(best)) {
            boolean helpless = best.hasEffect(ModEffects.WEBBED) || best.getHealth() < best.getMaxHealth() * 0.45F;
            if (helpless && this.canStartDrain(best)) {
                this.startDrain(best);
            }
        }
    }

    /** A rider-commanded web shot along the rider's crosshair, or release of the current line. */
    public void riderWeb(Player rider) {
        if (this.getWebMode() != WebMode.NONE) {
            this.releaseWeb(true);
            return;
        }
        if (this.webCooldown > 0) {
            return;
        }
        Vec3 eye = rider.getEyePosition();
        Vec3 end = eye.add(rider.getLookAngle().scale(WEB_RANGE));
        HitResult hit = this.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider));
        Vec3 aim = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        if (this.shootWeb(aim, WebPurpose.RIDER)) {
            this.webCooldown = 6;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && this.hasPassenger(attacker)) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            this.triggerAnim("action", "hurt");
            if (this.isDraining()) {
                this.damageTakenWhileDraining += amount;
            }
            if (this.getWebMode() == WebMode.TETHER && amount >= 6.0F) {
                this.releaseWeb(true);
            }
            if (this.isPerched()) {
                this.setPerched(false);
            }
            if (attacker instanceof Player player && !this.isTame() && player.getUUID().equals(this.tamerId)) {
                // betrayal while being tamed
                this.pacifiedTicks = 0;
                this.tamerId = null;
                this.entityData.set(DATA_TAMING, Math.max(0, this.getTamingProgress() - 30));
                this.playSound(ModSounds.SCREECH.get(), 1.2F, 0.8F);
            }
            if (this.level() instanceof ServerLevel server) {
                Vec3 p = this.getBodyCenter();
                server.sendParticles(ModParticles.BLOOD_DROP.get(), p.x, p.y, p.z, Math.min(16, 3 + (int) amount * 2), 0.5, 0.3, 0.5, 0.12);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            this.releaseWeb(false);
            this.stopDrain();
            this.setSurface(Direction.DOWN);
            if (this.hasHarness()) {
                this.spawnAtLocation(ModItems.HARNESS.get());
                this.setHarness(false);
            }
        }
        super.die(source);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isTame() && target instanceof GorewidowEntity other && other.isTame() && other.getOwnerUUID() != null
                && other.getOwnerUUID().equals(this.getOwnerUUID())) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof GorewidowEntity other && other.isTame() && other.getOwner() == owner) {
            return false;
        }
        return super.wantsToAttack(target, owner);
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(ModEffects.WEBBED) && !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    // ================================================================== taming & interaction
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = this.level().isClientSide;

        if (!this.isTame()) {
            if (stack.is(ModItems.BLOOD_VIAL.get())) {
                if (!client) {
                    this.feedForTaming(player, stack);
                }
                return InteractionResult.sidedSuccess(client);
            }
            return InteractionResult.PASS;
        }

        if (!this.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }

        if ((stack.is(ModItems.BLOOD_VIAL.get()) || stack.is(Tags.Items.FOODS_RAW_MEAT)) && this.getHealth() < this.getMaxHealth()) {
            if (!client) {
                float amount = stack.is(ModItems.BLOOD_VIAL.get()) ? 16.0F : 6.0F;
                this.heal(amount);
                this.consume(player, stack);
                this.playSound(ModSounds.PURR.get(), 1.0F, 1.0F);
                this.level().broadcastEntityEvent(this, (byte) 7);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.is(ModItems.HARNESS.get()) && !this.hasHarness()) {
            if (!client) {
                this.setHarness(true);
                this.consume(player, stack);
                this.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.is(Items.SHEARS) && this.hasHarness() && player.isSecondaryUseActive()) {
            if (!client) {
                this.setHarness(false);
                this.spawnAtLocation(ModItems.HARNESS.get());
                this.ejectPassengers();
                this.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.isEmpty()) {
            if (player.isSecondaryUseActive()) {
                if (!client) {
                    GorewidowCommand next = this.getCommand().next();
                    this.setCommand(next);
                    this.getNavigation().stop();
                    this.setTarget(null);
                    player.displayClientMessage(Component.translatable("message.gorewidow.command", next.label()), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (!this.hasHarness()) {
                if (!client) {
                    player.displayClientMessage(Component.translatable("message.gorewidow.needs_harness"), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (!this.isVehicle()) {
                if (!client) {
                    this.setCommand(GorewidowCommand.FOLLOW);
                    this.releaseWeb(false);
                    this.stopDrain();
                    this.setPerched(false);
                    if (player.startRiding(this)) {
                        this.triggerAnim("action", "mount");
                    }
                }
                return InteractionResult.sidedSuccess(client);
            }
        }
        return super.mobInteract(player, hand);
    }

    private void consume(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            ItemStack remainder = stack.getCraftingRemainingItem();
            stack.shrink(1);
            if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                player.drop(remainder, false);
            }
        }
    }

    private void feedForTaming(Player player, ItemStack stack) {
        if (this.tamerId != null && !this.tamerId.equals(player.getUUID()) && this.isPacified()) {
            player.displayClientMessage(Component.translatable("message.gorewidow.busy"), true);
            return;
        }
        this.consume(player, stack);
        this.tamerId = player.getUUID();
        this.pacifiedTicks = 600;
        this.ticksSinceFed = 0;
        if (this.getTarget() == player) {
            this.setTarget(null);
        }
        this.stopDrain();
        if (this.getWebMode() == WebMode.TETHER && this.getLinkedEntity() == player) {
            this.releaseWeb(true);
        }
        int gain = 14 + this.random.nextInt(9);
        int progress = Math.min(100, this.getTamingProgress() + gain);
        this.entityData.set(DATA_TAMING, progress);
        this.triggerAnim("action", "bite");
        this.playSound(ModSounds.DRAIN.get(), 1.0F, 1.2F);
        if (this.level() instanceof ServerLevel server) {
            Vec3 m = this.getMouthPos();
            server.sendParticles(ModParticles.BLOOD_DROP.get(), m.x, m.y, m.z, 8, 0.15, 0.1, 0.15, 0.05);
        }
        if (progress >= 100) {
            this.tame(player);
            this.setCommand(GorewidowCommand.FOLLOW);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setHealth(this.getMaxHealth());
            this.triggerAnim("action", "tame");
            this.playSound(ModSounds.PURR.get(), 1.5F, 1.0F);
            this.level().broadcastEntityEvent(this, (byte) 7);
            player.displayClientMessage(Component.translatable("message.gorewidow.tamed"), true);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
            player.displayClientMessage(Component.translatable("message.gorewidow.taming", progress), true);
        }
    }

    private void tickTamingDecay() {
        if (this.isTame() || this.getTamingProgress() <= 0) {
            return;
        }
        if (++this.ticksSinceFed > 1200 && this.tickCount % 40 == 0) {
            this.entityData.set(DATA_TAMING, this.getTamingProgress() - 1);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !this.isTame() && this.getTamingProgress() == 0 && !this.hasCustomName() && !this.isVehicle();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return this.isTame() || this.getTamingProgress() > 0 || super.requiresCustomPersistence();
    }

    // ================================================================== riding
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (this.isTame() && this.hasHarness() && this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player)) {
            return player;
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 eye = this.getRiderEyePos();
        move.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
    }

    /** Where the rider's eyes sit: under the thorax, between belly and surface. */
    public Vec3 getRiderEyePos() {
        return this.modelToWorld(0, 6.5F, -9.5F);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.surface == Direction.DOWN && !this.getWebMode().isRope()) {
            return super.getDismountLocationForPassenger(passenger);
        }
        Vec3 n = this.getBodyUp();
        Vec3 out = this.getBodyCenter().add(n.scale(-0.2)).subtract(0, passenger.getBbHeight() * 0.5, 0);
        AABB box = passenger.getDimensions(passenger.getPose()).makeBoundingBox(out);
        if (this.level().noCollision(passenger, box)) {
            return out;
        }
        Vec3 below = this.position().subtract(0, passenger.getBbHeight() + 0.1, 0);
        return this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(below))
                ? below : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        this.setRot(rider.getYRot(), rider.getXRot() * 0.5F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isControlledByLocalInstance() && this.pendingRiderJump > 0) {
            this.executeRiderLeap(rider, this.pendingRiderJump);
            this.pendingRiderJump = 0;
        }
    }

    private void executeRiderLeap(Player rider, float power) {
        Vec3 look = rider.getLookAngle();
        WebMode mode = this.getWebMode();
        if (mode.isRope()) {
            // let go of the line, keeping the swing's momentum plus a kick
            Vec3 v = this.getDeltaMovement();
            Vec3 boost = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(0.35 * power) : look.scale(0.3 * power);
            this.setDeltaMovement(v.add(boost).add(0, 0.25 * power, 0));
            this.entityData.set(DATA_WEB_MODE, (byte) WebMode.NONE.ordinal());
            PacketDistributor.sendToServer(new MountActionPayload(MountActionPayload.Action.RELEASE_WEB));
        } else if (this.surface != Direction.DOWN) {
            Vec3 n = this.getSurfaceNormal();
            this.setSurface(Direction.DOWN);
            this.setDeltaMovement(n.scale(0.45 + 0.25 * power).add(look.scale(0.5 + 0.7 * power)));
        } else if (this.onGround()) {
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
            this.setDeltaMovement(flat.scale(0.45 + 0.85 * power).add(0, 0.5 + 0.45 * power, 0));
        } else {
            return;
        }
        this.hasImpulse = true;
        this.resetFallDistance();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        float strafe = rider.xxa * 0.7F;
        float forward = rider.zza;
        if (forward < 0) {
            forward *= 0.45F;
        }
        return new Vec3(strafe, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.1F;
    }

    @Override
    public void onPlayerJump(int power) {
        if (!this.hasHarness()) {
            return;
        }
        this.pendingRiderJump = power >= 90 ? 1.0F : 0.35F + 0.65F * Math.max(0, power) / 90.0F;
    }

    @Override
    public boolean canJump() {
        return this.hasHarness() && !this.isDeadOrDying();
    }

    @Override
    public void handleStartJump(int power) {
        this.triggerAnim("action", "leap");
        this.playSound(ModSounds.LEAP.get(), 1.0F, 1.0F);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public int getJumpCooldown() {
        return 10;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof Player) {
            this.getNavigation().stop();
            this.setTarget(null);
            this.setPerched(false);
        }
    }

    // ================================================================== sounds
    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isPerched() ? null : ModSounds.AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // footsteps are emitted per leg plant by the client-side gait rig
    }

    @Override
    protected float getSoundVolume() {
        return 1.2F;
    }

    // ================================================================== spawning
    public static boolean checkSpawnRules(EntityType<GorewidowEntity> type, net.minecraft.world.level.ServerLevelAccessor level,
                                          net.minecraft.world.entity.MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        if (!below.isValidSpawn(level, pos.below(), type) && !below.is(BlockTags.LEAVES)) {
            return false;
        }
        // hunts at night, or by day under thick canopy / in caves
        return net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random)
                || (!level.canSeeSky(pos) && random.nextInt(3) == 0);
    }

    // ================================================================== GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementState));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("web_shoot", WEB_SHOOT)
                .triggerableAnim("hurt", HURT)
                .triggerableAnim("screech", SCREECH)
                .triggerableAnim("tame", TAME)
                .triggerableAnim("mount", MOUNT)
                .triggerableAnim("land", LAND)
                .triggerableAnim("leap", LEAP));
    }

    private PlayState movementState(AnimationState<GorewidowEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (this.isDraining()) {
            return state.setAndContinue(DRAIN);
        }
        WebMode mode = this.getWebMode();
        if (mode == WebMode.HANG && !this.onGround()) {
            return state.setAndContinue(HANG);
        }
        if (mode == WebMode.SWING && !this.onGround()) {
            return state.setAndContinue(SWING);
        }
        if (mode == WebMode.TETHER) {
            return state.setAndContinue(WEB_PULL);
        }
        float speed = this.clientSpeed;
        if (this.surface != Direction.DOWN) {
            if (this.isPerched()) {
                return state.setAndContinue(PERCHED);
            }
            return state.setAndContinue(speed > 0.03F ? CLIMB : CLING);
        }
        if (!this.onGround() && !this.isInWater() && !this.isPassenger() && Math.abs(this.getDeltaMovement().y) > 0.12) {
            return state.setAndContinue(AIR);
        }
        if (this.isInSittingPose()) {
            return state.setAndContinue(SIT);
        }
        if (speed > 0.24F) {
            return state.setAndContinue(RUN);
        }
        if (speed > 0.02F) {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // ================================================================== misc
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (this.surface != Direction.DOWN || this.getWebMode().isRope()) {
            this.resetFallDistance();
            return;
        }
        super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean isPushable() {
        return !this.isVehicle() && super.isPushable();
    }

    @Override
    public void push(Entity entity) {
        if (!this.hasPassenger(entity)) {
            super.push(entity);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // long legs reach well past the hitbox, and a web line can stretch far away
        AABB box = this.getBoundingBox().inflate(2.2, 1.0, 2.2);
        WebMode mode = this.getWebMode();
        if (mode.isRope()) {
            box = box.minmax(new AABB(this.getAnchor(), this.getAnchor()).inflate(0.5));
        } else if (mode.hasLine()) {
            Entity linked = this.getLinkedEntity();
            if (linked != null) {
                box = box.minmax(linked.getBoundingBox());
            }
        }
        return box;
    }

    @Override
    public boolean canBeLeashed() {
        return this.isTame() && super.canBeLeashed();
    }
}
