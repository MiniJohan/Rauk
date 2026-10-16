package rauk.com.entity.nav;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import rauk.com.Rauk;

/**
 * What one block state means to Rauk getting about (design doc 4): the parts of it that are in the
 * way, what it can stand on, which faces it can dig its nails into. Worked out once for each state
 * and kept, so the route finder never asks the game about shapes again, and the hotel's map can be
 * worked out away from the game's own thread.
 */
public final class NavBlock {
	public static final TagKey<Block> UNCLIMBABLE = TagKey.create(Registries.BLOCK, Rauk.id("unclimbable"));
	/** Blocks no taller than this (carpets) are walked over, not around. */
	public static final double LOW = 0.2;
	private static final AABB[] NONE = {};
	private static final Map<BlockState, NavBlock> CACHE = new ConcurrentHashMap<>();

	public static final NavBlock AIR = new NavBlock(NONE, NONE, false, false, 0, 0, false, false, true);
	/** Anything the map knows nothing of: solid, grippable stone. */
	public static final NavBlock SOLID = new NavBlock(new AABB[] {new AABB(0, 0, 0, 1, 1, 1)}, new AABB[] {new AABB(0, 0, 0, 1, 1, 1)},
			true, false, 0x3F, 0x3F, false, false, false);

	/** Its solid parts in the block's own space (0 to 1), doors it can open counted open, low things left out. */
	public final AABB[] boxes;
	/** What can be stood on: the solid parts, low things included, doors, gates and hatches left out. */
	public final AABB[] tops;
	/** In its way: something taller than a carpet that it can't open. */
	public final boolean blocked;
	/** A wooden door, gate or hatch it opens on its way (a locked one is a wall). */
	public final boolean openable;
	/** The faces (by 3D data value) it can grip, dry; and wet, where only smoothness doesn't stop it. */
	private final int grip, gripWet;
	public final boolean water;
	/** A ladder, a vine: something someone on foot climbs. */
	public final boolean climbable;
	public final boolean air;

	private NavBlock(AABB[] boxes, AABB[] tops, boolean blocked, boolean openable, int grip, int gripWet, boolean water, boolean climbable,
			boolean air) {
		this.boxes = boxes;
		this.tops = tops;
		this.blocked = blocked;
		this.openable = openable;
		this.grip = grip;
		this.gripWet = gripWet;
		this.water = water;
		this.climbable = climbable;
		this.air = air;
	}

	public static NavBlock of(BlockState state) {
		if (state.isAir()) {
			return AIR;
		}
		NavBlock b = CACHE.get(state);
		if (b == null) {
			b = compute(state);
			CACHE.put(state, b);
		}
		return b;
	}

	/** Whether it can hold on to this face of the block, with or without water at its hands. */
	public boolean grips(Direction face, boolean wet) {
		return ((wet ? gripWet : grip) & (1 << face.get3DDataValue())) != 0;
	}

	/** Wooden doors, gates and trapdoors count as open, unless locked (a locked door is a wall to it). */
	public static boolean openable(BlockState state) {
		return (state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WOODEN_TRAPDOORS))
				&& !rauk.com.block.hotel.HotelDoorBlock.locked(state);
	}

	private static NavBlock compute(BlockState state) {
		EmptyBlockGetter none = EmptyBlockGetter.INSTANCE;
		boolean openable = openable(state);
		VoxelShape shape = state.getCollisionShape(none, BlockPos.ZERO);
		boolean low = !shape.isEmpty() && shape.max(Direction.Axis.Y) <= LOW;
		boolean blocked = !openable && !shape.isEmpty() && !low;
		BlockState open = openable && state.hasProperty(BlockStateProperties.OPEN) ? state.setValue(BlockStateProperties.OPEN, true) : state;
		VoxelShape inWay = openable ? open.getCollisionShape(none, BlockPos.ZERO) : shape;
		AABB[] boxes = low || inWay.isEmpty() ? NONE : inWay.toAabbs().toArray(NONE);
		AABB[] tops = openable || shape.isEmpty() ? NONE : shape.toAabbs().toArray(NONE);
		int grip = 0, wet = 0;
		boolean chain = state.is(Blocks.CHAIN);
		boolean smooth = state.is(UNCLIMBABLE);
		for (Direction face : Direction.values()) {
			boolean sturdy = blocked && state.isFaceSturdy(none, BlockPos.ZERO, face);
			if (chain || sturdy) {
				wet |= 1 << face.get3DDataValue();
				if (chain || !smooth) {
					grip |= 1 << face.get3DDataValue();
				}
			}
		}
		return new NavBlock(boxes, tops, blocked, openable, grip, wet, !state.getFluidState().isEmpty(), state.is(BlockTags.CLIMBABLE), false);
	}

	/** Every state's info worked out now, on the game's thread, before something away from it needs them. */
	public static void prepare(List<BlockState> states) {
		for (BlockState s : states) {
			if (s != null) {
				of(s);
			}
		}
	}
}
