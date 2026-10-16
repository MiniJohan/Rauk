package rauk.com.hotel;

import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import rauk.com.Rauk;

/**
 * Puts the hotel's plan into the world: a chunk at a time during world generation, all at once
 * for the test command. Only blocks that differ are set, so writing it again repairs it.
 */
public final class HotelWriter {
	private static BlockState[][] rotated;

	private HotelWriter() {
	}

	/** The plan's palette as block states, turned each of the four ways. */
	static synchronized BlockState[] states(Rotation rotation) {
		if (rotated == null) {
			List<String> palette = HotelBuilder.plan().palette();
			BlockState[] base = new BlockState[palette.size()];
			for (int i = 1; i < base.length; i++) {
				if (palette.get(i).startsWith("#")) {
					// A marker the builder used while it worked, not left in the plan.
					base[i] = Blocks.AIR.defaultBlockState();
					continue;
				}
				try {
					base[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), palette.get(i), false).blockState();
				} catch (CommandSyntaxException e) {
					Rauk.LOGGER.error("Hotel plan has a bad block {}: {}", palette.get(i), e.getMessage());
					base[i] = Blocks.STONE_BRICKS.defaultBlockState();
				}
			}
			rotated = new BlockState[Rotation.values().length][];
			for (Rotation r : Rotation.values()) {
				BlockState[] turned = new BlockState[base.length];
				for (int i = 1; i < base.length; i++) {
					turned[i] = base[i].rotate(r);
				}
				rotated[r.ordinal()] = turned;
			}
		}
		return rotated[rotation.ordinal()];
	}

	/**
	 * Writes the plan's blocks that fall inside a box (a chunk, or everything) and between two
	 * heights in the plan. With {@code clear} it removes them instead (to cut the hotel open for
	 * filming).
	 */
	public static int write(LevelAccessor level, HotelFrame frame, BoundingBox clip, int flags, int yMin, int yMax, boolean clear) {
		HotelPlan plan = HotelBuilder.plan();
		BlockState[] states = states(frame.rotation());
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
			for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
				BlockPos column = frame.toWorld(x, 0, z);
				if (clip != null && (column.getX() < clip.minX() || column.getX() > clip.maxX() || column.getZ() < clip.minZ()
						|| column.getZ() > clip.maxZ())) {
					continue;
				}
				for (int y = Math.max(yMin, HotelPlan.Y0); y <= Math.min(yMax, HotelPlan.Y0 + HotelPlan.SY - 1); y++) {
					int id = plan.at(x, y, z);
					if (id == 0) {
						continue;
					}
					pos.set(column.getX(), frame.origin().getY() + y, column.getZ());
					BlockState want = clear ? air : states[id];
					if (level.getBlockState(pos) != want) {
						level.setBlock(pos, want, flags);
						changed++;
						// In a chunk still being generated, every block of a door that keeps a block
						// entity is marked to get one; only the lower half does.
						if (want.getBlock() instanceof rauk.com.block.hotel.HotelDoorBlock
								&& want.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
								&& level.getChunk(pos) instanceof net.minecraft.world.level.chunk.ProtoChunk proto) {
							proto.removeBlockEntity(pos);
						}
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Shapes the ground round the hotel during world generation, within one chunk: inside the
	 * footprint, earth above the lobby floor is dug away and any gap under the building is filled
	 * with masonry down to the ground; round it, the ground slopes smoothly from the lobby's level
	 * out to where it was, over {@link HotelFrame#MARGIN} blocks. Over the sea that makes it a
	 * limestone stack. Each column is shaped from its own height, so it comes out the same in
	 * whatever order the chunks are made.
	 */
	public static void shapeGround(WorldGenLevel level, HotelFrame frame, BoundingBox chunk, int seaLevel) {
		HotelPlan plan = HotelBuilder.plan();
		int ground = frame.origin().getY() - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
		BlockState foundation = Blocks.STONE_BRICKS.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
		BlockState stone = Blocks.STONE.defaultBlockState(), limestone = Blocks.CALCITE.defaultBlockState();
		for (int wz = chunk.minZ(); wz <= chunk.maxZ(); wz++) {
			for (int wx = chunk.minX(); wx <= chunk.maxX(); wx++) {
				int[] p = frame.toPlan(wx, 0, wz);
				// How far out from the building's walls (the U and its slanted front, the courtyard inside).
				double d = Math.max(0, Math.ceil(HotelPlan.outside(p[0] + 0.5, p[2] + 0.5) - 0.5));
				if (d > HotelFrame.MARGIN) {
					continue;
				}
				int natural = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, wx, wz) - 1;
				boolean wet = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz) - 1 > natural;
				BlockState top = level.getBlockState(pos.set(wx, natural, wz));
				if (top.isAir() || !top.getFluidState().isEmpty()) {
					top = Blocks.GRASS_BLOCK.defaultBlockState();
				}
				if (d == 0) {
					// Under the building: masonry down to the ground. Over it: nothing but the hotel.
					for (int y = natural + 1; y < ground; y++) {
						if (plan.at(p[0], y - frame.origin().getY(), p[2]) == 0) {
							level.setBlock(pos.set(wx, y, wz), foundation, 2);
						}
					}
					for (int y = ground + 1; y <= Math.max(natural, seaLevel); y++) {
						if (plan.at(p[0], y - frame.origin().getY(), p[2]) == 0) {
							level.setBlock(pos.set(wx, y, wz), air, 2);
						}
					}
					continue;
				}
				double t = d / HotelFrame.MARGIN;
				t = t * t * (3 - 2 * t);
				int target = (int) Math.round(ground + (natural - ground) * t);
				if (natural < target) {
					boolean sea = wet || natural < seaLevel - 1;
					for (int y = natural + 1; y <= target; y++) {
						BlockState fill = sea ? limestone : y == target ? (top.is(BlockTags.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : top)
								: y > target - 3 ? dirt : stone;
						if (sea && y == target && y > seaLevel) {
							fill = Blocks.GRASS_BLOCK.defaultBlockState();
						} else if (sea && y > target - 3 && y > seaLevel) {
							fill = dirt;
						}
						level.setBlock(pos.set(wx, y, wz), fill, 2);
					}
				} else if (natural > target) {
					int floor = Math.max(target, seaLevel);
					for (int y = floor + 1; y <= natural; y++) {
						level.setBlock(pos.set(wx, y, wz), y <= seaLevel ? water : air, 2);
					}
					if (floor == target) {
						level.setBlock(pos.set(wx, target, wz), top, 2);
					}
				}
				// Clear anything standing on the old surface over the new one (plants, snow).
				for (int y = Math.max(natural, target) + 1; y <= Math.max(natural, target) + 2; y++) {
					BlockState s = level.getBlockState(pos.set(wx, y, wz));
					if (!s.isAir() && s.getFluidState().isEmpty() && !s.isSolid()) {
						level.setBlock(pos, air, 2);
					}
				}
			}
		}
	}
}
