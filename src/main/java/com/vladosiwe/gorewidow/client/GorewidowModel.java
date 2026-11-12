package com.vladosiwe.gorewidow.client;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Loads geo/entity/gorewidow.geo.json, animations/entity/gorewidow.animation.json and the texture. */
public class GorewidowModel extends DefaultedEntityGeoModel<GorewidowEntity> {
    public GorewidowModel() {
        super(Gorewidow.id("gorewidow"));
    }

    @Override
    public void setCustomAnimations(GorewidowEntity animatable, long instanceId, AnimationState<GorewidowEntity> animationState) {
        GorewidowRigs.get(animatable).applyToModel(this, animatable, animationState);
    }

    @Override
    public RenderType getRenderType(GorewidowEntity animatable, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
