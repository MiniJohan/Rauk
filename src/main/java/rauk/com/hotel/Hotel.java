package rauk.com.hotel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import rauk.com.Rauk;

/**
 * The hotel (hotel design): its world generation registered, the world's hotel found when the
 * server starts, and its rules kept every tick ({@link HotelRules}).
 */
public final class Hotel {
	public static final StructureType<RaukvikGrand> STRUCTURE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			Rauk.id("raukvik_grand"), () -> RaukvikGrand.CODEC);
	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Rauk.id("hotel"),
			(StructurePieceType.ContextlessType) HotelPiece::new);
	public static final StructurePlacementType<HotelPlacement> PLACEMENT = Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT,
			Rauk.id("hotel_site"), () -> HotelPlacement.CODEC);
	public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, Rauk.id("raukvik_grand"));
	public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, Rauk.id("raukvik_grand"));

	private Hotel() {
	}

	public static void init() {
		HotelLights.init();
		HotelLifts.init();
		HotelProgress.init();
		Generator.init();
		CollectorsShelves.init();
		LockdownRelease.init();
		HotelSync.init();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> discover(server.overworld()));
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD) {
				HotelRules.tick(level);
				HotelHeat.tick(level);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !HotelRules.protects(level, player, pos));
	}

	/**
	 * Finds this world's hotel, if it has one: works out its site and reads the structure there
	 * (generating no more of the world than its plan).
	 */
	private static void discover(ServerLevel level) {
		HotelData data = HotelData.get(level);
		if (data.frame() != null) {
			return;
		}
		var state = level.getChunkSource().getGeneratorState();
		if (state.possibleStructureSets().stream().noneMatch(set -> set.is(SET))) {
			return;
		}
		var site = HotelSite.of(state);
		Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
		StructureStart start = level.getChunk(site.x, site.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			Rauk.LOGGER.info("No Raukvik Grand at {} (made before the hotel was added, or with structures off?)", site);
			return;
		}
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof HotelPiece hotel) {
				data.set(hotel.frame(), true);
				Rauk.LOGGER.info("The Raukvik Grand is at {}, turned {}", hotel.frame().center(), hotel.frame().rotation());
			}
		}
	}
}
