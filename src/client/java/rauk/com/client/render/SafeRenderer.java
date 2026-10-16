package rauk.com.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.state.BlockState;
import rauk.com.block.hotel.SafeBlock;
import rauk.com.block.hotel.SafeEntity;
import rauk.com.hotel.HotelFurniture;

/**
 * Draws the figure each of the reception safe's dials is turned to (puzzle 1), small and black in
 * the enamel window over it, while the safe is shut.
 */
public final class SafeRenderer implements BlockEntityRenderer<SafeEntity> {
	private final Font font;

	public SafeRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	public static void init() {
		BlockEntityRenderers.register(SafeBlock.entityType, SafeRenderer::new);
	}

	@Override
	public void render(SafeEntity safe, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState state = safe.getBlockState();
		if (!(state.getBlock() instanceof SafeBlock block) || block.state(state) != SafeBlock.SHUT) {
			return;
		}
		pose.pushPose();
		// The safe is drawn facing north, its front at z = 0; turned to the way it faces.
		pose.translate(0.5, 0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-(block.facingOf(state).toYRot() + 180)));
		pose.translate(-0.5, 0, -0.5);
		float scale = 0.0145f;
		for (int i = 0; i < 4; i++) {
			String figure = Integer.toString(safe.dial(i));
			pose.pushPose();
			pose.translate(HotelFurniture.SAFE_DIALS[i] / 16, 10.75 / 16, 0.15 / 16);
			pose.mulPose(Axis.YP.rotationDegrees(180));
			pose.scale(scale, -scale, scale);
			font.drawInBatch(figure, -font.width(figure) / 2f, -3.5f, 0xFF1C1814, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
					light);
			pose.popPose();
		}
		pose.popPose();
	}
}
