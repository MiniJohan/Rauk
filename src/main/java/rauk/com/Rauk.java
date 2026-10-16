package rauk.com;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rauk.com.entity.OtherCreatures;
import rauk.com.entity.mind.Prey;
import rauk.com.entity.mind.ScentData;
import rauk.com.hotel.Hotel;
import rauk.com.hotel.HotelCommands;
import rauk.com.network.RaukNetwork;
import rauk.com.registry.HotelBlocks;
import rauk.com.registry.RaukBlocks;
import rauk.com.registry.RaukEntities;
import rauk.com.registry.RaukItems;
import rauk.com.sound.RaukSounds;

public class Rauk implements ModInitializer {
	public static final String MOD_ID = "rauk";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RaukBlocks.init();
		HotelBlocks.init();
		RaukSounds.init();
		rauk.com.sound.HotelSounds.init();
		RaukEntities.init();
		RaukItems.init();
		OtherCreatures.init();
		Hotel.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
			RaukCommands.register(dispatcher);
			HotelCommands.register(dispatcher);
		});
		RaukNetwork.init();
		// Every player leaves a scent trail wherever they go.
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.players().isEmpty()) {
				return;
			}
			ScentData scent = ScentData.get(level);
			long now = level.getGameTime();
			for (var player : level.players()) {
				if (Prey.is(player)) {
					scent.lay(player, now);
				}
			}
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
