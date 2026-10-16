package rauk.com.client.render;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import rauk.com.Rauk;
import rauk.com.client.anim.Quat;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.model.RaukModel;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;

public class RaukRenderer extends MobRenderer<RaukEntity, RaukModel> {
	private static final ResourceLocation TEXTURE = Rauk.id("textures/entity/rauk.png");

	public RaukRenderer(EntityRendererProvider.Context context) {
		super(context, new RaukModel(context.bakeLayer(RaukModel.LAYER)), 1.1f);
		addLayer(new RaukEyesLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(RaukEntity entity) {
		return TEXTURE;
	}

	/** The procedural body runs first, so the rotation and the model below use this frame's pose. */
	@Override
	public void render(RaukEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
			int packedLight) {
		RaukAnimator.of(entity).update(entity, partialTick);
		super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
		CarcassRenderer.render(entity, partialTick, poseStack, buffers, packedLight);
	}

	/**
	 * The body is placed on the surface it holds and turned by the animator's orientation: upright
	 * on a floor, sideways on a wall, upside down on a ceiling.
	 */
	@Override
	protected void setupRotations(RaukEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick,
			float scale) {
		RaukAnimator animator = RaukAnimator.of(entity);
		V offset = animator.rootOffset;
		Quat q = animator.orientation;
		poseStack.translate(offset.x(), offset.y(), offset.z());
		poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
		poseStack.mulPose(Axis.YP.rotationDegrees(180));
	}

	/** The model is built at 64 units per block, four times vanilla's 16. */
	@Override
	protected void scale(RaukEntity entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.25f, 0.25f, 0.25f);
	}
}
