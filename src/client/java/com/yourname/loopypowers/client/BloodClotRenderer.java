package com.yourname.loopypowers.client;

import com.yourname.loopypowers.entity.BloodClotEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
// tells server to render nothing for this entity
public class BloodClotRenderer extends EntityRenderer<BloodClotEntity> {

    public BloodClotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BloodClotEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // intentionally empty
    }

    @Override
    public Identifier getTexture(BloodClotEntity entity) {
        return null;
    }
}