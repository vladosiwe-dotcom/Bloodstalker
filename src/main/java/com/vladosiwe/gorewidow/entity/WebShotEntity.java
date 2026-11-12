package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModParticles;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A glob of silk fired from the spinneret. It flies straight (silk is light and fast) and reports
 * where it stuck back to its Gorewidow, which turns that into a swing anchor or a tether.
 */
public class WebShotEntity extends Projectile {
    public static final int MAX_LIFE = (int) Math.ceil(GorewidowEntity.WEB_RANGE / GorewidowEntity.WEB_SHOT_SPEED) + 1;
    private int life;

    public WebShotEntity(EntityType<? extends WebShotEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WebShotEntity(Level level, GorewidowEntity owner, Vec3 from, Vec3 velocity) {
        this(ModEntities.WEB_SHOT.get(), level);
        this.setOwner(owner);
        this.setPos(from.x, from.y - this.getBbHeight() * 0.5, from.z);
        this.setDeltaMovement(velocity);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, ClipContext.Block.COLLIDER);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
            if (this.isRemoved()) {
                return;
            }
        }
        Vec3 v = this.getDeltaMovement();
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                double t = this.random.nextDouble();
                this.level().addParticle(ModParticles.WEB_FIBER.get(),
                        this.getX() - v.x * t, this.getY() + this.getBbHeight() * 0.5 - v.y * t, this.getZ() - v.z * t,
                        (this.random.nextDouble() - 0.5) * 0.02, -0.01, (this.random.nextDouble() - 0.5) * 0.02);
            }
        } else if (++this.life > MAX_LIFE) {
            if (this.getOwner() instanceof GorewidowEntity widow) {
                widow.onWebMissed();
            }
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!(target instanceof LivingEntity) || !super.canHitEntity(target)) {
            return false;
        }
        if (this.getOwner() instanceof GorewidowEntity widow) {
            if (target == widow || widow.hasPassenger(target) || target.isPassengerOfSameVehicle(widow)) {
                return false;
            }
            if (widow.isTame()) {
                LivingEntity owner = widow.getOwner();
                if (target == owner || (target instanceof TamableAnimal pet && pet.isTame() && owner != null && pet.isOwnedBy(owner))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            if (this.getOwner() instanceof GorewidowEntity widow) {
                widow.onWebAnchored(result.getLocation(), result.getDirection(), result.getBlockPos());
            }
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            if (this.getOwner() instanceof GorewidowEntity widow && result.getEntity() instanceof LivingEntity living) {
                widow.onWebTethered(living);
            }
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }
}
