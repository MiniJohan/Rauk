package rauk.com.hotel;

import java.util.Arrays;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Raukvik Grand as a world structure (hotel design 7): one per world, where
 * {@link HotelPlacement} puts it. It stands with its back to the sea when there is sea beside it,
 * its lobby floor at the lie of the land under it.
 */
public class RaukvikGrand extends Structure {
	public static final MapCodec<RaukvikGrand> CODEC = simpleCodec(RaukvikGrand::new);

	public RaukvikGrand(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
		Rotation rotation = facingSea(context, cx, cz);
		int[] middle = HotelFrame.rotate(HotelPlan.WIDTH / 2, HotelPlan.TOTAL_DEPTH / 2, rotation);
		int ox = cx - middle[0], oz = cz - middle[1];
		// The lobby floor at the middle height of the ground under the footprint, and above the sea.
		int[] heights = new int[9];
		int i = 0;
		for (int px : new int[] {4, HotelPlan.WIDTH / 2, HotelPlan.WIDTH - 5}) {
			for (int pz : new int[] {4, HotelPlan.TOTAL_DEPTH / 2, HotelPlan.TOTAL_DEPTH - 20}) {
				int[] r = HotelFrame.rotate(px, pz, rotation);
				heights[i++] = context.chunkGenerator().getBaseHeight(ox + r[0], oz + r[1], Heightmap.Types.WORLD_SURFACE_WG,
						context.heightAccessor(), context.randomState());
			}
		}
		Arrays.sort(heights);
		int sea = context.chunkGenerator().getSeaLevel();
		int y = Math.max(heights[4], sea + 3);
		HotelFrame frame = new HotelFrame(new BlockPos(ox, y, oz), rotation);
		rauk.com.Rauk.LOGGER.info("Hotel structure: lobby at y {} (ground {} to {}), turned {}, biome {}", y, heights[0], heights[8], rotation,
				context.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(cx), net.minecraft.core.QuartPos.fromBlock(y),
						net.minecraft.core.QuartPos.fromBlock(cz), context.randomState().sampler()).unwrapKey().orElse(null));
		return Optional.of(new GenerationStub(new BlockPos(cx, y, cz), pieces -> pieces.addPiece(new HotelPiece(frame))));
	}

	/**
	 * Which way to turn the hotel: its back (the plan's north) toward whichever side has the most
	 * sea within a hundred blocks or so; if there's none, any way.
	 */
	private static Rotation facingSea(GenerationContext context, int x, int z) {
		int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		Rotation[] turns = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
		int best = -1, most = 0;
		for (int d = 0; d < 4; d++) {
			int sea = 0;
			for (int r = 40; r <= 120; r += 20) {
				int bx = x + directions[d][0] * r, bz = z + directions[d][1] * r;
				if (context.biomeSource().getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(64), QuartPos.fromBlock(bz),
						context.randomState().sampler()).is(BiomeTags.IS_OCEAN)) {
					sea++;
				}
			}
			if (sea > most) {
				most = sea;
				best = d;
			}
		}
		return best < 0 ? Rotation.getRandom(context.random()) : turns[best];
	}

	@Override
	public StructureType<?> type() {
		return Hotel.STRUCTURE;
	}
}
