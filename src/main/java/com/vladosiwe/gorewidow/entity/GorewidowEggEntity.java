package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModItems;
import com.vladosiwe.gorewidow.registry.ModParticles;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A placed, incubating egg. It needs warmth: within three blocks of a lit campfire, fire, lava,
 * magma, a lit furnace, a torch or a lantern it incubates; left cold it slowly dies. When done it
 * hatches into a baby that belongs to the parents' owner and carries the inherited stats.
 */
public class GorewidowEggEntity extends Entity {
    /** Ticks of warmth needed to hatch. */
    public static final int INCUBATION_TICKS = 6000;
    private static final EntityDataAccessor<Float> DATA_PROGRESS = SynchedEntityData.defineId(GorewidowEggEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(GorewidowEggEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WARM = SynchedEntityData.defineId(GorewidowEggEntity.class, EntityDataSerializers.BOOLEAN);

    private CompoundTag eggData = new CompoundTag();

    public GorewidowEggEntity(EntityType<? extends GorewidowEggEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PROGRESS, 0.0F);
        builder.define(DATA_HEALTH, 100.0F);
        builder.define(DATA_WARM, false);
    }

    public void setEggData(CompoundTag data) {
        this.eggData = data.copy();
        this.entityData.set(DATA_PROGRESS, data.getFloat("Progress"));
    }

    public float getProgress() {
        return this.entityData.get(DATA_PROGRESS);
    }

    public float getEggHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public boolean isWarm() {
        return this.entityData.get(DATA_WARM);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        if (this.level().isClientSide) {
            if (this.isWarm() && this.random.nextInt(12) == 0) {
                this.level().addParticle(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0.02, 0);
            }
            return;
        }
        if (this.tickCount % 20 != 0) {
            return;
        }
        boolean warm = this.isNearHeat();
        this.entityData.set(DATA_WARM, warm);
        float health = this.getEggHealth();
        if (warm) {
            float progress = Math.min(1.0F, this.getProgress() + 20.0F / INCUBATION_TICKS);
            this.entityData.set(DATA_PROGRESS, progress);
            this.entityData.set(DATA_HEALTH, Math.min(100.0F, health + 1.0F));
            if (progress >= 1.0F) {
                this.hatch();
            }
        } else {
            health -= 2.0F;
            this.entityData.set(DATA_HEALTH, health);
            if (health <= 0) {
                if (this.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
                }
                this.playSound(ModSounds.DEATH.get(), 0.3F, 2.0F);
                this.discard();
            }
        }
    }

    private boolean isNearHeat() {
        BlockPos center = this.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
            BlockState state = this.level().getBlockState(pos);
            if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.FIRE) || state.is(Blocks.TORCH)
                    || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)
                    || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
                    || (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT))) {
                return true;
            }
        }
        return false;
    }

    /** Hatches now; also used by game tests. */
    public GorewidowEntity hatch() {
        GorewidowEntity baby = ModEntities.GOREWIDOW.get().create(this.level());
        if (baby == null) {
            return null;
        }
        baby.moveTo(this.getX(), this.getY(), this.getZ(), this.random.nextFloat() * 360, 0);
        baby.getStats().load(this.eggData.getCompound("Child"));
        baby.setFemale(this.random.nextBoolean());
        baby.getNursery().setupNewborn();
        if (this.eggData.hasUUID("Owner")) {
            UUID owner = this.eggData.getUUID("Owner");
            baby.setTame(true, false);
            baby.setOwnerUUID(owner);
            baby.getMind().addTrust(owner, 50.0F);
        }
        baby.getCare().applyStats(true);
        baby.setPersistenceRequired();
        this.level().addFreshEntity(baby);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.WEB_FIBER.get(), this.getX(), this.getY() + 0.3, this.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        }
        this.playSound(ModSounds.PURR.get(), 1.0F, 1.8F);
        if (baby.getOwner() instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.gorewidow.hatched", baby.getStats().level()), false);
        }
        this.discard();
        return baby;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            player.displayClientMessage(Component.translatable(this.isWarm() ? "message.gorewidow.egg_warm" : "message.gorewidow.egg_cold",
                    Math.round(this.getProgress() * 100), Math.round(this.getEggHealth())), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        // punching it picks it up, keeping its progress
        CompoundTag data = this.eggData.copy();
        data.putFloat("Progress", this.getProgress());
        ItemStack stack = new ItemStack(ModItems.EGG.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        this.spawnAtLocation(stack);
        this.discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.eggData = tag.getCompound("Egg");
        this.entityData.set(DATA_PROGRESS, tag.getFloat("Progress"));
        this.entityData.set(DATA_HEALTH, tag.contains("EggHealth") ? tag.getFloat("EggHealth") : 100.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Egg", this.eggData);
        tag.putFloat("Progress", this.getProgress());
        tag.putFloat("EggHealth", this.getEggHealth());
    }
}
