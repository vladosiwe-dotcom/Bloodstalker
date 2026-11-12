package com.vladosiwe.gorewidow.client;

import com.vladosiwe.gorewidow.entity.GorewidowAnatomy;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Client-side procedural rig for one Gorewidow.
 *
 * <ul>
 *   <li>Body frame: a smoothed orientation that follows floor / wall / ceiling / web line.</li>
 *   <li>Gait: every foot is planted in the world and only re-placed (with an arcing step onto a
 *   ray-cast surface point) when the body has moved too far from it; legs step in two alternating
 *   groups like a real spider.</li>
 *   <li>IK: an analytic coxa-yaw + two-bone solve per leg reaches the planted foot, with the
 *   tarsus held near-vertical; the body pitches, rolls and rises to match the terrain.</li>
 *   <li>Poses: curled, spread, raised and gripping leg poses blended in for states where feet are
 *   not on a surface (death, swinging, jumps, bites, feeding).</li>
 * </ul>
 * Keyframed GeckoLib animations drive the body, head, fangs, palps and abdomen; all leg bones and
 * the "tilt"/"neck" bones belong to this rig and are written every frame.
 */
public final class GorewidowRig {
    private static final int LEGS = GorewidowAnatomy.LEG_COUNT;
    private static final float PX = 1.0F / 16.0F;

    // ---------------------------------------------------------------- body frame
    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f up = new Vector3f(0, 1, 0);
    private boolean initialised;
    private float originOffset = 0.9F;
    private long lastNanos;
    /** World-space model origin for the current frame. */
    private Vec3 origin = Vec3.ZERO;
    private Vec3 lastTickPos = Vec3.ZERO;

    // ---------------------------------------------------------------- gait
    private final Vec3[] planted = new Vec3[LEGS];
    private final Vec3[] stepFrom = new Vec3[LEGS];
    private final Vec3[] stepTo = new Vec3[LEGS];
    private final int[] stepTick = new int[LEGS];
    private final int[] stepLength = new int[LEGS];
    private final boolean[] stepping = new boolean[LEGS];
    private final Vec3[] footRender = new Vec3[LEGS];
    private float stepHeight = 0.4F;
    private float ikWeight;
    private float ikTarget;

    // ---------------------------------------------------------------- pose blend weights
    private float curl;
    private float spread;
    private float raise;
    private float grip;

    // ---------------------------------------------------------------- web line visuals
    @Nullable
    private Vec3 retractFrom;
    private int retractTicks;
    private WebMode lastMode = WebMode.NONE;
    @Nullable
    private Vec3 lastLineEnd;
    private float twang;
    private boolean wasTaut;
    /** Spinneret / mouth positions relative to the entity render origin, from bone tracking. */
    private Vec3 spinneretLocal = Vec3.ZERO;
    private Vec3 headLocal = Vec3.ZERO;

    GorewidowRig() {
    }

    // ================================================================= orientation (render thread)

    /** Computes this frame's smoothed body orientation; returns the origin offset from the entity render position. */
    public Vec3 updateFrame(GorewidowEntity widow, float partialTick) {
        long now = System.nanoTime();
        float dt = this.lastNanos == 0 ? 0.05F : Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
        this.lastNanos = now;

        Vec3 targetUp;
        Vec3 targetFwd;
        WebMode mode = widow.getWebMode();
        boolean roped = mode.isRope() && !widow.onGround();
        if (!roped && widow.isOnFloor()) {
            float yaw = Mth.rotLerp(partialTick, widow.yBodyRotO, widow.yBodyRot) * Mth.DEG_TO_RAD;
            targetUp = new Vec3(0, 1, 0);
            targetFwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        } else {
            targetUp = widow.getBodyUp();
            targetFwd = widow.getBodyForward();
        }
        Quaternionf target = basis(targetFwd, targetUp);
        if (!this.initialised) {
            this.orientation.set(target);
            this.initialised = true;
        } else if (!widow.isDeadOrDying()) {
            float rate = roped ? 7.0F : 11.0F;
            this.orientation.slerp(target, 1.0F - (float) Math.exp(-rate * dt));
        }
        this.orientation.normalize();
        this.orientation.transform(new Vector3f(0, 1, 0), this.up);

        float targetOffset = roped ? GorewidowAnatomy.BODY_HEIGHT * PX : widow.getBbHeight() * 0.5F;
        this.originOffset += (targetOffset - this.originOffset) * (1.0F - (float) Math.exp(-8.0F * dt));

        Vec3 lerped = widow.getPosition(partialTick);
        Vec3 offset = new Vec3(0, widow.getBbHeight() * 0.5, 0).subtract(new Vec3(this.up).scale(this.originOffset));
        this.origin = lerped.add(offset);
        return offset;
    }

    public Quaternionf orientation() {
        return this.orientation;
    }

    public Vec3 up() {
        return new Vec3(this.up);
    }

    public Vec3 forward() {
        Vector3f f = this.orientation.transform(new Vector3f(0, 0, -1));
        return new Vec3(f);
    }

    public boolean isInitialised() {
        return this.initialised;
    }

    /** World-space position of a point given in internal model pixels. */
    public Vec3 modelToWorld(float x, float y, float z) {
        Vector3f v = this.orientation.transform(new Vector3f(x * PX, y * PX, z * PX));
        return this.origin.add(v.x, v.y, v.z);
    }

    private Vec3 worldToModelPx(Vec3 world) {
        Vector3f v = new Vector3f((float) (world.x - this.origin.x), (float) (world.y - this.origin.y), (float) (world.z - this.origin.z));
        new Quaternionf(this.orientation).conjugate().transform(v);
        return new Vec3(v.x / PX, v.y / PX, v.z / PX);
    }

    private static Quaternionf basis(Vec3 fwd, Vec3 up) {
        Vec3 u = up.normalize();
        Vec3 f = fwd.subtract(u.scale(fwd.dot(u)));
        if (f.lengthSqr() < 1.0E-6) {
            f = Math.abs(u.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
            f = f.subtract(u.scale(f.dot(u)));
        }
        f = f.normalize();
        Vec3 x = f.cross(u);
        Matrix3f m = new Matrix3f(
                (float) x.x, (float) x.y, (float) x.z,
                (float) u.x, (float) u.y, (float) u.z,
                (float) -f.x, (float) -f.y, (float) -f.z);
        return m.getNormalizedRotation(new Quaternionf());
    }

    // ================================================================= gait (client tick)

    public void tick(GorewidowEntity widow) {
        if (!this.initialised) {
            return;
        }
        WebMode mode = widow.getWebMode();
        boolean supported = !widow.isDeadOrDying() && (widow.onGround() || !widow.isOnFloor()) && !(mode.isRope() && !widow.onGround());
        this.ikTarget = supported ? 1.0F : 0.0F;

        // web line bookkeeping for the retract effect and tension twang
        Vec3 lineEnd = this.currentLineEnd(widow, 1.0F);
        if (mode == WebMode.NONE && this.lastMode != WebMode.NONE && this.lastLineEnd != null) {
            this.retractFrom = this.lastLineEnd;
            this.retractTicks = 7;
        } else if (this.retractTicks > 0) {
            this.retractTicks--;
        }
        if (lineEnd != null) {
            this.lastLineEnd = lineEnd;
        }
        if (mode.isRope()) {
            double dist = widow.getBodyCenter().distanceTo(widow.getAnchor());
            boolean taut = dist > widow.getRopeLength() - 0.15;
            if (taut && !this.wasTaut) {
                this.twang = Math.min(0.3F, 0.08F + (float) widow.getDeltaMovement().length() * 0.25F);
            }
            this.wasTaut = taut;
        } else {
            this.wasTaut = false;
        }
        this.twang *= 0.82F;
        this.lastMode = mode;

        Vec3 pos = widow.position();
        Vec3 velocity = pos.subtract(this.lastTickPos);
        this.lastTickPos = pos;
        double speed = velocity.length();
        if (speed > 4.0) {
            // teleported: re-plant everything
            java.util.Arrays.fill(this.planted, null);
            velocity = Vec3.ZERO;
            speed = 0;
        }

        // advance steps in progress
        for (int i = 0; i < LEGS; i++) {
            if (this.stepping[i] && ++this.stepTick[i] >= this.stepLength[i]) {
                this.stepping[i] = false;
                this.planted[i] = this.stepTo[i];
                if (supported && widow.level().random.nextInt(3) == 0) {
                    Vec3 f = this.planted[i];
                    widow.level().playLocalSound(f.x, f.y, f.z, ModSounds.STEP.get(), SoundSource.HOSTILE,
                            0.18F + (float) speed * 0.6F, 0.85F + widow.level().random.nextFloat() * 0.35F, false);
                }
            }
        }
        if (!supported) {
            java.util.Arrays.fill(this.stepping, false);
            java.util.Arrays.fill(this.planted, null);
            return;
        }

        int stepTicks = Mth.clamp(Math.round(6.0F - (float) speed * 10.0F), 3, 6);
        this.stepHeight = 0.3F + (float) Math.min(0.5, speed * 1.2);
        double threshold = 0.42 + Math.min(0.35, speed * 0.6);
        Vec3 lead = velocity.scale(stepTicks * 0.9 + 2.0);

        int activeGroup = -1;
        for (int i = 0; i < LEGS; i++) {
            if (this.stepping[i]) {
                activeGroup = GorewidowAnatomy.LEG_GROUP[i];
                break;
            }
        }
        double[] error = new double[LEGS];
        double[] groupError = new double[2];
        Vec3[] rest = new Vec3[LEGS];
        for (int i = 0; i < LEGS; i++) {
            float[] r = GorewidowAnatomy.REST_FOOT[i];
            rest[i] = this.modelToWorld(r[0], r[1], r[2]);
            if (this.planted[i] == null) {
                this.planted[i] = this.probe(widow, rest[i]);
                continue;
            }
            error[i] = this.planted[i].distanceTo(rest[i].add(lead.scale(0.5)));
            if (error[i] > 3.0) {
                this.planted[i] = this.probe(widow, rest[i]);
                this.stepping[i] = false;
                error[i] = 0;
            }
            groupError[GorewidowAnatomy.LEG_GROUP[i]] += error[i];
        }
        if (activeGroup < 0) {
            activeGroup = groupError[0] >= groupError[1] ? 0 : 1;
        }
        for (int i = 0; i < LEGS; i++) {
            if (this.stepping[i] || GorewidowAnatomy.LEG_GROUP[i] != activeGroup) {
                continue;
            }
            boolean otherGroupStepping = false;
            for (int j = 0; j < LEGS; j++) {
                if (this.stepping[j] && GorewidowAnatomy.LEG_GROUP[j] != activeGroup) {
                    otherGroupStepping = true;
                    break;
                }
            }
            if (otherGroupStepping) {
                continue;
            }
            if (error[i] > threshold || (error[i] > threshold * 0.55 && this.anyStepping(activeGroup))) {
                this.stepFrom[i] = this.planted[i];
                this.stepTo[i] = this.probe(widow, rest[i].add(lead));
                this.stepTick[i] = 0;
                this.stepLength[i] = stepTicks;
                this.stepping[i] = true;
            }
        }
    }

    private boolean anyStepping(int group) {
        for (int i = 0; i < LEGS; i++) {
            if (this.stepping[i] && GorewidowAnatomy.LEG_GROUP[i] == group) {
                return true;
            }
        }
        return false;
    }

    /** Finds the surface point under a desired foot position, searching along the body's down axis. */
    private Vec3 probe(GorewidowEntity widow, Vec3 desired) {
        Vec3 u = new Vec3(this.up);
        Vec3 from = desired.add(u.scale(1.4));
        Vec3 to = desired.subtract(u.scale(1.8));
        BlockHitResult hit = widow.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return desired.subtract(u.scale(0.6));
    }

    @Nullable
    public Vec3 currentLineEnd(GorewidowEntity widow, float partialTick) {
        WebMode mode = widow.getWebMode();
        if (mode.isRope()) {
            return widow.getAnchor();
        }
        if (mode == WebMode.SHOT || mode == WebMode.TETHER) {
            Entity linked = widow.getLinkedEntity();
            if (linked != null) {
                return linked.getPosition(partialTick).add(0, linked.getBbHeight() * 0.5, 0);
            }
        }
        return null;
    }

    // ================================================================= per-frame bone solve

    public void applyToModel(GeoModel<GorewidowEntity> model, GorewidowEntity widow, AnimationState<GorewidowEntity> state) {
        float partial = state.getPartialTick();
        float blend = 1.0F - (float) Math.exp(-10.0F * 0.016F);

        model.getBone("harness").ifPresent(b -> b.setHidden(!widow.hasHarness()));

        // ---- pose weights
        WebMode mode = widow.getWebMode();
        boolean dead = widow.isDeadOrDying();
        boolean roped = mode.isRope() && !widow.onGround();
        boolean airborne = !dead && !roped && widow.isOnFloor() && !widow.onGround() && !widow.isPassenger()
                && Math.abs(widow.getDeltaMovement().y) > 0.1;
        String action = currentAction(widow);
        float curlTarget = dead || mode == WebMode.HANG && roped ? 1.0F : 0.0F;
        float spreadTarget = (roped && mode == WebMode.SWING) || airborne ? 1.0F : 0.0F;
        float raiseTarget = !dead && (mode == WebMode.TETHER || "screech".equals(action) || "web_shoot".equals(action)) ? 1.0F
                : "bite".equals(action) ? 0.75F : 0.0F;
        float gripTarget = !dead && (widow.isDraining() || widow.getTameFeeder() != null
                || (widow.isMounting() && widow.getMountProgress(partial) < 0.85F)) ? 1.0F : 0.0F;
        this.ikWeight += (this.ikTarget - this.ikWeight) * blend;
        this.curl += (curlTarget - this.curl) * blend * 0.7F;
        this.spread += (spreadTarget - this.spread) * blend;
        this.raise += (raiseTarget - this.raise) * blend * 1.5F;
        this.grip += (gripTarget - this.grip) * blend;

        // ---- feet for this frame
        for (int i = 0; i < LEGS; i++) {
            float[] r = GorewidowAnatomy.REST_FOOT[i];
            Vec3 rest = this.modelToWorld(r[0], r[1], r[2]);
            if (this.planted[i] == null) {
                this.footRender[i] = rest;
            } else if (this.stepping[i]) {
                float t = Mth.clamp((this.stepTick[i] + partial) / this.stepLength[i], 0, 1);
                float s = t * t * (3 - 2 * t);
                Vec3 p = this.stepFrom[i].lerp(this.stepTo[i], s);
                this.footRender[i] = p.add(new Vec3(this.up).scale(Math.sin(Math.PI * t) * this.stepHeight));
            } else {
                this.footRender[i] = this.planted[i];
            }
        }

        // ---- body tilt / height from the feet
        GeoBone tilt = model.getBone("tilt").orElse(null);
        if (tilt != null) {
            double front = 0, back = 0, left = 0, right = 0, all = 0;
            for (int i = 0; i < LEGS; i++) {
                double h = this.worldToModelPx(this.footRender[i]).y * PX;
                all += h;
                int idx = i % 4;
                if (idx < 2) front += h; else back += h;
                if (i < 4) left += h; else right += h;
            }
            front /= 4; back /= 4; left /= 4; right /= 4; all /= LEGS;
            float w = this.ikWeight;
            float pitch = (float) Mth.clamp(Math.atan2(front - back, 2.4) * 0.8, -0.45, 0.45) * w;
            float roll = (float) Mth.clamp(-Math.atan2(left - right, 3.6) * 0.8, -0.4, 0.4) * w;
            tilt.setRotX(pitch);
            tilt.setRotZ(roll);
            tilt.setRotY(0);
            tilt.setPosY((float) Mth.clamp(all * 16 * 0.6, -6, 8) * w - this.curl * 3.0F);
        }

        // ---- neck look
        model.getBone("neck").ifPresent(neck -> {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            boolean look = data != null && widow.isOnFloor() && !roped && !dead;
            float yaw = look ? Mth.clamp(data.netHeadYaw(), -40, 40) * Mth.DEG_TO_RAD : 0;
            float pitch = look ? Mth.clamp(data.headPitch(), -30, 30) * Mth.DEG_TO_RAD : 0;
            neck.setRotY(yaw);
            neck.setRotX(pitch);
        });

        // ---- body bone chain in model space (px)
        Matrix4f bodyMatrix = new Matrix4f();
        for (String name : new String[]{"root", "tilt", "body"}) {
            GeoBone b = model.getBone(name).orElse(null);
            if (b != null) {
                applyBone(bodyMatrix, b);
            }
        }
        Matrix4f invBody = new Matrix4f(bodyMatrix).invert();

        float time = (widow.tickCount + partial) * 0.05F;
        for (int i = 0; i < LEGS; i++) {
            String name = GorewidowAnatomy.LEG_NAMES[i];
            GeoBone coxa = model.getBone(name + "_coxa").orElse(null);
            GeoBone femur = model.getBone(name + "_femur").orElse(null);
            GeoBone tibia = model.getBone(name + "_tibia").orElse(null);
            GeoBone tarsus = model.getBone(name + "_tarsus").orElse(null);
            if (coxa == null || femur == null || tibia == null || tarsus == null) {
                continue;
            }
            int c = GorewidowAnatomy.LEG_CHAIN_SIGN[i];
            float psi0 = GorewidowAnatomy.REST_YAW[i];
            float[] hip = GorewidowAnatomy.HIP[i];

            // IK solution toward the planted foot
            Vec3 footModel = this.worldToModelPx(this.footRender[i]);
            Vector3f fb = invBody.transformPosition(new Vector3f((float) footModel.x, (float) footModel.y, (float) footModel.z));
            float[] ik = solve(fb.x - hip[0], fb.y - hip[1], fb.z - hip[2], c,
                    GorewidowAnatomy.FEMUR[i], GorewidowAnatomy.TIBIA[i]);
            ik[0] = Mth.wrapDegrees((ik[0] - psi0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;

            // poses (yaw offset, femur elevation, tibia relative, tarsus relative)
            int idx = i % 4;
            float breathe = Mth.sin(time * 6 + i) * 0.04F;
            float[] pose = {0, (float) Math.toRadians(35) + breathe, (float) Math.toRadians(-95), (float) Math.toRadians(-40)};
            pose = mix(pose, new float[]{0, (float) Math.toRadians(14), (float) Math.toRadians(-55) + breathe, (float) Math.toRadians(-25)}, this.spread);
            pose = mix(pose, new float[]{0, (float) Math.toRadians(78), (float) Math.toRadians(-155), (float) Math.toRadians(-60)}, this.curl);

            float[] angles = mix(pose, ik, this.ikWeight);
            if (idx == 0) {
                float[] raised = {(float) Math.toRadians(-18), (float) Math.toRadians(68) + Mth.sin(time * 18) * 0.06F,
                        (float) Math.toRadians(-38), (float) Math.toRadians(-20)};
                float[] gripping = {(float) Math.toRadians(-28), (float) Math.toRadians(42),
                        (float) Math.toRadians(-118) + Mth.sin(time * 10) * 0.08F, (float) Math.toRadians(-50)};
                angles = mix(angles, raised, this.raise * (1 - this.grip));
                angles = mix(angles, gripping, this.grip);
            } else if (idx == 1 && this.grip > 0) {
                float[] gripping = {(float) Math.toRadians(-12), (float) Math.toRadians(38), (float) Math.toRadians(-110), (float) Math.toRadians(-45)};
                angles = mix(angles, gripping, this.grip * 0.6F);
            }

            coxa.setRotY(angles[0]);
            coxa.setRotX(0);
            coxa.setRotZ(0);
            femur.setRotZ(c * angles[1]);
            femur.setRotX(0);
            femur.setRotY(0);
            tibia.setRotZ(c * angles[2]);
            tibia.setRotX(0);
            tibia.setRotY(0);
            tarsus.setRotZ(c * angles[3]);
            tarsus.setRotX(0);
            tarsus.setRotY(0);
        }
    }

    /** Captures tracked bone positions after the model was drawn (relative to the entity render origin). */
    public void captureTrackedBones(GeoModel<GorewidowEntity> model) {
        model.getBone("spinneret").ifPresent(b -> {
            Vector3f t = b.getLocalSpaceMatrix().getTranslation(new Vector3f());
            this.spinneretLocal = new Vec3(t);
        });
        model.getBone("head").ifPresent(b -> {
            Vector3f t = b.getLocalSpaceMatrix().getTranslation(new Vector3f());
            this.headLocal = new Vec3(t);
        });
    }

    public Vec3 spinneretLocal() {
        return this.spinneretLocal;
    }

    public Vec3 headLocal() {
        return this.headLocal;
    }

    @Nullable
    public Vec3 retractFrom() {
        return this.retractTicks > 0 ? this.retractFrom : null;
    }

    public float retractProgress(float partialTick) {
        return 1.0F - Mth.clamp((this.retractTicks - partialTick) / 7.0F, 0, 1);
    }

    public float twang() {
        return this.twang;
    }

    // ================================================================= helpers

    @Nullable
    private static String currentAction(GorewidowEntity widow) {
        AnimatableManager<?> manager = widow.getAnimatableInstanceCache().getManagerForId(widow.getId());
        if (manager == null) {
            return null;
        }
        AnimationController<?> controller = manager.getAnimationControllers().get("action");
        if (controller == null || controller.getAnimationState() == AnimationController.State.STOPPED
                || controller.getCurrentAnimation() == null) {
            return null;
        }
        return controller.getCurrentAnimation().animation().name();
    }

    private static void applyBone(Matrix4f m, GeoBone b) {
        m.translate(-b.getPosX(), b.getPosY(), b.getPosZ());
        m.translate(b.getPivotX(), b.getPivotY(), b.getPivotZ());
        m.rotateZ(b.getRotZ()).rotateY(b.getRotY()).rotateX(b.getRotX());
        m.scale(b.getScaleX(), b.getScaleY(), b.getScaleZ());
        m.translate(-b.getPivotX(), -b.getPivotY(), -b.getPivotZ());
    }

    /**
     * Analytic leg solve in body space (model px). Returns {absolute yaw, femur elevation,
     * tibia elevation relative to femur, tarsus elevation relative to tibia}.
     */
    static float[] solve(double dx, double dy, double dz, int c, float femur, float tibia) {
        double psi = Math.atan2(-c * dz, c * dx);
        double r = Math.max(2.0, Math.sqrt(dx * dx + dz * dz) - GorewidowAnatomy.COXA);
        double te = GorewidowAnatomy.TARSUS_ELEVATION;
        double tx = r - Math.cos(te) * GorewidowAnatomy.TARSUS;
        double ty = dy - Math.sin(te) * GorewidowAnatomy.TARSUS;
        double d = Mth.clamp(Math.sqrt(tx * tx + ty * ty), Math.abs(femur - tibia) + 0.05, femur + tibia - 0.05);
        double beta = Math.atan2(ty, tx);
        double alpha = Math.acos(Mth.clamp((femur * femur + d * d - tibia * tibia) / (2 * femur * d), -1, 1));
        double ef = beta + alpha;
        double kx = femur * Math.cos(ef);
        double ky = femur * Math.sin(ef);
        double et = Math.atan2(ty - ky, tx - kx);
        return new float[]{(float) psi, (float) ef, (float) (et - ef), (float) (te - et)};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        return out;
    }

    static boolean shouldTick(GorewidowEntity widow) {
        return widow.isAlive() || widow.deathTime < 40;
    }
}
