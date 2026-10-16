package rauk.com.hotel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import rauk.com.Rauk;
import rauk.com.mixin.StructureStateAccessor;

/**
 * Where in a world the hotel goes (hotel design 7): a spot set by the world seed, 1,500 to 3,500
 * blocks from the middle of the world in a direction of its own, then moved to the nearest bit of
 * coast within about 600 blocks: land, with the sea a little way off to one side. If there's no
 * coast near, the nearest land; failing that, the spot itself. Worked out once a world.
 */
public final class HotelSite {
	private static final Map<Long, ChunkPos> SITES = new ConcurrentHashMap<>();
	private static final int MIN_FROM_SPAWN = 1000, MAX_FROM_SPAWN = 5000;

	private HotelSite() {
	}

	/** The chunk the hotel is centered on, in the world this generator state belongs to. */
	public static ChunkPos of(ChunkGeneratorStructureState state) {
		return SITES.computeIfAbsent(state.getLevelSeed(),
				seed -> find(seed, ((StructureStateAccessor) state).rauk$biomeSource(), state.randomState().sampler()));
	}

	/** The site already worked out for a seed, or null. */
	public static ChunkPos known(long seed) {
		return SITES.get(seed);
	}

	private static ChunkPos find(long seed, BiomeSource biomes, Climate.Sampler sampler) {
		RandomSource random = RandomSource.create(seed ^ 0x5241554B1931L);
		double angle = random.nextDouble() * Math.PI * 2, distance = 1500 + random.nextDouble() * 2000;
		int x0 = (int) Math.round(Math.cos(angle) * distance), z0 = (int) Math.round(Math.sin(angle) * distance);
		int[] land = null;
		for (int ring = 0; ring <= 19; ring++) {
			double radius = ring * 32;
			int n = ring == 0 ? 1 : (int) Math.ceil(2 * Math.PI * radius / 32);
			for (int i = 0; i < n; i++) {
				double a = 2 * Math.PI * i / n;
				int x = x0 + (int) Math.round(Math.cos(a) * radius), z = z0 + (int) Math.round(Math.sin(a) * radius);
				double fromSpawn = Math.hypot(x, z);
				if (fromSpawn < MIN_FROM_SPAWN || fromSpawn > MAX_FROM_SPAWN || !isLand(biomes, sampler, x, z)) {
					continue;
				}
				if (land == null) {
					land = new int[] {x, z};
				}
				if (coastal(biomes, sampler, x, z)) {
					Rauk.LOGGER.info("The Raukvik Grand stands on the coast at {} {}", x, z);
					return new ChunkPos(x >> 4, z >> 4);
				}
			}
		}
		int[] at = land != null ? land : new int[] {x0, z0};
		Rauk.LOGGER.info("The Raukvik Grand stands inland at {} {}", at[0], at[1]);
		return new ChunkPos(at[0] >> 4, at[1] >> 4);
	}

	private static Holder<Biome> biome(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
		return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z), sampler);
	}

	private static boolean isLand(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
		Holder<Biome> b = biome(biomes, sampler, x, z);
		return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_RIVER);
	}

	/** Land at least 28 blocks deep one way, with the sea 64 blocks off that way. */
	private static boolean coastal(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
		for (int d = 0; d < 8; d++) {
			double a = Math.PI / 4 * d;
			double cos = Math.cos(a), sin = Math.sin(a);
			if (biome(biomes, sampler, x + (int) (cos * 64), z + (int) (sin * 64)).is(BiomeTags.IS_OCEAN)
					&& isLand(biomes, sampler, x + (int) (cos * 28), z + (int) (sin * 28))) {
				return true;
			}
		}
		return false;
	}
}
