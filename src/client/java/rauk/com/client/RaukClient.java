package rauk.com.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import rauk.com.client.combat.RaukCombatClient;
import rauk.com.client.debug.RaukDebugRenderer;
import rauk.com.client.dev.DevShot;
import rauk.com.client.fx.RaukEffects;
import rauk.com.client.model.RaukModel;
import rauk.com.client.render.RaukRenderer;
import rauk.com.client.sound.RaukSoundClient;
import rauk.com.registry.RaukBlocks;
import rauk.com.registry.RaukEntities;

public class RaukClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(RaukModel.LAYER, RaukModel::createBodyLayer);
		EntityRendererRegistry.register(RaukEntities.RAUK, RaukRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(RaukBlocks.BLOOD, RenderType.translucent());
		// The hotel's ornate iron is see-through between its bars.
		for (var m : rauk.com.hotel.HotelMaterials.ALL) {
			if (m.shape() == rauk.com.hotel.HotelMaterials.Shape.RAILING) {
				BlockRenderLayerMap.INSTANCE.putBlock(rauk.com.registry.HotelBlocks.get(m.id()), RenderType.cutout());
			}
		}
		// The slanted windows, once their shutters are up, are glass.
		BlockRenderLayerMap.INSTANCE.putBlock(rauk.com.registry.HotelBlocks.get("slant_glass"), RenderType.cutout());
		// Furniture has holes in it: crystal drops, fringes, fronds, torn velvet.
		rauk.com.registry.HotelBlocks.FURNITURE.values().forEach(b -> BlockRenderLayerMap.INSTANCE.putBlock(b, RenderType.cutout()));
		rauk.com.client.render.GlowModels.init();
		rauk.com.client.render.HotelDoorRenderer.init();
		rauk.com.client.render.SafeRenderer.init();
		rauk.com.client.render.ClockRenderer.init();
		RaukDebugRenderer.init();
		RaukCombatClient.init();
		RaukEffects.init();
		rauk.com.client.fx.LiftRide.init();
		rauk.com.client.fx.HotelAtmosphere.init();
		rauk.com.block.hotel.FurnitureBlock.clientPower = rauk.com.client.fx.HotelAtmosphere::power;
		// The flashlight: its beam lights the world, and its model shows it on or off.
		rauk.com.client.fx.Flashlight.init();
		net.minecraft.client.renderer.item.ItemProperties.register(rauk.com.registry.RaukItems.FLASHLIGHT, rauk.com.Rauk.id("lit"),
				(stack, level, entity, seed) -> rauk.com.item.FlashlightItem.lit(stack) ? 1 : 0);
		RaukSoundClient.init();
		DevShot.init();
	}
}
