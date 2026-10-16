package rauk.com.client.render;

import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes the lit parts of the hotel's lamps glow (hotel design 6.3): a lamp shade, a bulb, the
 * fire in a grate drawn at full brightness, whatever light falls on it. The furniture's lit models
 * ({@code ..._lit}) mark their glowing faces with tint index {@value #GLOW}; those faces are given
 * an emissive material as the block is drawn, and the mark taken off.
 */
public final class GlowModels {
	public static final int GLOW = 7;

	private GlowModels() {
	}

	public static void init() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyModelAfterBake().register((model, context) -> {
			ResourceLocation id = context.resourceId();
			if (model != null && id != null && id.getNamespace().equals("rauk") && id.getPath().endsWith("_lit")) {
				return new Glowing(model);
			}
			return model;
		}));
	}

	private static final class Glowing extends ForwardingBakedModel {
		Glowing(BakedModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				super.emitBlockQuads(level, state, pos, random, context);
				return;
			}
			context.pushTransform(quad -> {
				if (quad.colorIndex() == GLOW) {
					quad.colorIndex(-1);
					quad.material(renderer.materialFinder().copyFrom(quad.material()).emissive(true).disableDiffuse(true)
							.ambientOcclusion(TriState.FALSE).find());
				}
				return true;
			});
			super.emitBlockQuads(level, state, pos, random, context);
			context.popTransform();
		}
	}
}
