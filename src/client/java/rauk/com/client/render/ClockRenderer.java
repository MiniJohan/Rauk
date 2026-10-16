package rauk.com.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import rauk.com.Rauk;
import rauk.com.block.hotel.ClockBlock;
import rauk.com.block.hotel.ClockEntity;

/**
 * Draws the study clock's hands where they're set (puzzle 5): two thin black iron hands over its
 * bare face, the minute hand long, the hour hand short and thicker, turning clockwise as someone in
 * front of the clock sees it.
 */
public final class ClockRenderer implements BlockEntityRenderer<ClockEntity> {
	/** The middle of the face, and its front, in the clock's own sixteenths (drawn facing north, three blocks high). */
	private static final float CX = 8, CY = 39, FRONT = 4.6f;

	public ClockRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static void init() {
		BlockEntityRenderers.register(ClockBlock.entityType, ClockRenderer::new);
	}

	@Override
	public void render(ClockEntity clock, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState state = clock.getBlockState();
		if (!(state.getBlock() instanceof ClockBlock block)) {
			return;
		}
		TextureAtlasSprite iron = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(Rauk.id("block/f_iron"));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-(block.facingOf(state).toYRot() + 180)));
		pose.translate(-0.5, 0, -0.5);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		int m = clock.minutes();
		hand(pose, vc, iron, (m % 60) / 60.0, 3.6f, 0.5f, FRONT - 0.15f, light, overlay);
		hand(pose, vc, iron, (m / 60.0) / 12.0, 2.4f, 0.8f, FRONT - 0.1f, light, overlay);
		pose.popPose();
	}

	/** A hand from the middle of the face, a turn's fraction round from twelve, so long and so wide, at a depth. */
	private static void hand(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, double turn, float length, float width, float z, int light,
			int overlay) {
		double a = turn * Math.PI * 2;
		// Clockwise to someone in front: from up towards their right, which is -x here.
		float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
		float px = -dy * width / 2, py = dx * width / 2;
		float bx = CX - dx * 0.6f, by = CY - dy * 0.6f, tx = CX + dx * length, ty = CY + dy * length;
		Matrix4f m = pose.last().pose();
		PoseStack.Pose p = pose.last();
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		vc.addVertex(m, bx - px, by - py, z).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, -1);
		vc.addVertex(m, bx + px, by + py, z).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, -1);
		vc.addVertex(m, tx + px, ty + py, z).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, -1);
		vc.addVertex(m, tx - px, ty - py, z).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, -1);
	}
}
