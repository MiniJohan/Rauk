package rauk.com.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import rauk.com.Rauk;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.model.RaukModel;
import rauk.com.entity.RaukEntity;

/**
 * The eyeshine (design doc 9): a cold silver-green that only shows when light falls on its eyes
 * and the head faces you, hidden while it blinks. Only the two eye parts are drawn.
 */
final class RaukEyesLayer extends RenderLayer<RaukEntity, RaukModel> {
	private static final ResourceLocation EYES = Rauk.id("textures/entity/rauk_eyes.png");

	RaukEyesLayer(RenderLayerParent<RaukEntity, RaukModel> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, RaukEntity entity, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		float shine = RaukAnimator.of(entity).eyeshine(entity, partialTick);
		if (shine <= 0.01f || entity.isDeadOrDying()) {
			return;
		}
		RaukModel model = getParentModel();
		int c = Math.round(255 * Math.min(1, shine));
		try {
			model.showOnly(i -> model.eye[i]);
			model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(EYES)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					0xFF000000 | c << 16 | c << 8 | c);
		} finally {
			model.showAll();
		}
	}
}
