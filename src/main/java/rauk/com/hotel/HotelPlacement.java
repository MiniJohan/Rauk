package rauk.com.hotel;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Places exactly one hotel in a world, at its {@link HotelSite}. It is a random-spread placement
 * only so that {@code /locate} and explorer maps can find it: every region it is asked about
 * answers with the same one chunk.
 */
public class HotelPlacement extends RandomSpreadStructurePlacement {
	public static final MapCodec<HotelPlacement> CODEC = MapCodec.unit(HotelPlacement::new);

	public HotelPlacement() {
		super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0f, 1931, Optional.empty(), 64, 16, RandomSpreadType.LINEAR);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
		ChunkPos site = HotelSite.of(state);
		boolean here = site.x == x && site.z == z;
		if (here) {
			rauk.com.Rauk.LOGGER.info("Hotel placement: chunk {} {} is the site", x, z);
		}
		return here;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
		ChunkPos site = HotelSite.known(seed);
		return site != null ? site : super.getPotentialStructureChunk(seed, regionX, regionZ);
	}

	@Override
	public StructurePlacementType<?> type() {
		return Hotel.PLACEMENT;
	}
}
