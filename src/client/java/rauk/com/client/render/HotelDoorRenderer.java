package rauk.com.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import rauk.com.Rauk;
import rauk.com.block.hotel.HotelDoorBlock;
import rauk.com.block.hotel.HotelDoorEntity;
import rauk.com.registry.HotelBlocks;

/**
 * Draws a hotel door's panel where its swing has got to (hotel design 4b): the panel's model,
 * turned on its hinges by the door's angle, between frames as well as on them. The door block
 * itself has no model of its own; this is all there is to see of it.
 */
public final class HotelDoorRenderer implements BlockEntityRenderer<HotelDoorEntity> {
	private final ModelBlockRenderer blocks;

	public HotelDoorRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.getBlockRenderDispatcher().getModelRenderer();
	}

	public static void init() {
		ModelLoadingPlugin.register(ctx -> {
			for (String[] d : HotelBlocks.DOOR_TYPES) {
				ctx.addModels(panel(d[0], true), panel(d[0], false));
			}
		});
		BlockEntityRenderers.register(HotelDoorBlock.entityType, HotelDoorRenderer::new);
	}

	/** The panel model of a door, hinged on the left or the right. */
	public static ResourceLocation panel(String door, boolean left) {
		return Rauk.id("block/" + door + "_panel_" + (left ? "left" : "right"));
	}

	@Override
	public void render(HotelDoorEntity door, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState state = door.getBlockState();
		if (!(state.getBlock() instanceof HotelDoorBlock)) {
			return;
		}
		boolean left = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		BakedModel model = Minecraft.getInstance().getModelManager().getModel(panel(id, left));
		float facing = state.getValue(DoorBlock.FACING).toYRot() + 180;
		float angle = door.angle(partial);
		pose.pushPose();
		// Drawn facing north, closed along the block's south edge; turned to the door's facing.
		pose.translate(0.5, 0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing));
		pose.translate(-0.5, 0, -0.5);
		// Then swung on its hinges, at the left or right end of that edge.
		double px = (left ? 1.5 : 14.5) / 16, pz = 14.5 / 16;
		pose.translate(px, 0, pz);
		pose.mulPose(Axis.YP.rotationDegrees(left ? angle : -angle));
		pose.translate(-px, 0, -pz);
		blocks.renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model, 1, 1, 1, light(door, light), overlay);
		pose.popPose();
	}

	/** The brighter of the light at the door's two blocks, so a lamp by its top lights it too. */
	private static int light(HotelDoorEntity door, int below) {
		if (door.getLevel() == null) {
			return below;
		}
		BlockPos up = door.getBlockPos().above();
		int above = LevelRenderer.getLightColor(door.getLevel(), up);
		return LightTexture.pack(Math.max(LightTexture.block(below), LightTexture.block(above)),
				Math.max(LightTexture.sky(below), LightTexture.sky(above)));
	}
}
