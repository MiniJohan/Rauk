package rauk.com.entity.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What Rauk knows of its hotel (design doc 6.1, territory): the doors, gates, trapdoors, levers and
 * lights it has seen, and how they normally are. It learns a place the first time it sees it.
 * After that, anything it sees changed (a door left open, a light switched on, a lamp gone,
 * something left on the floor) is an anomaly it wants to investigate. Changes it made itself,
 * like the doors it opens, don't count. A change that stays for long enough becomes the new
 * normal.
 *
 * <p>It knows its own hotel: the first time it takes in a place it learns everything there, seen or
 * not. After that it only notices changes it can actually see: each check needs a clear line from
 * its eyes.
 */
public final class Territory {
	/** How long it must have known a place before a new thing there counts as new. */
	private static final long SETTLE = 400;
	/** How long a change must stay before it's accepted as how things are now. */
	private static final long ACCEPT = 6000;
	private static final int SIGHT = 24;
	private static final boolean DEBUG = Boolean.getBoolean("rauk.devshot");

	public enum Kind { DOOR_OPENED, DOOR_CLOSED, LIGHT_ON, LIGHT_OFF, LIGHT_ADDED, LIGHT_GONE, LEVER, ITEM_LEFT }

	/** Something it noticed has changed, and whether it has been to look yet. */
	public record Anomaly(BlockPos pos, Kind kind, long time) {
	}

	/** The state it remembers for each interesting block, as a block state id. */
	private final Map<Long, Integer> normal = new HashMap<>();
	/** A different state it has seen, and since when, waiting to be accepted. */
	private final Map<Long, long[]> changed = new HashMap<>();
	/** When each 16-block section was first looked at. */
	private final Map<Long, Long> known = new HashMap<>();
	/** States it caused itself, so it doesn't startle at its own doors. */
	private final Map<Long, Integer> expected = new HashMap<>();
	private final Set<UUID> itemsSeen = new HashSet<>();
	private final List<long[]> ignored = new ArrayList<>();
	public final List<Anomaly> anomalies = new ArrayList<>();
	private int sectionCursor;

	/** Whether a block is something it keeps track of. */
	public static boolean interesting(BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		// Not the hotel's invisible fill lights, which come and go with the chandeliers.
		return state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES) || state.getBlock() instanceof LeverBlock
				|| state.hasProperty(BlockStateProperties.LIT) || state.getLightEmission() > 0 && !state.is(net.minecraft.world.level.block.Blocks.LIGHT);
	}

	/** Rauk changed this block itself: expect the new state. */
	public void expect(BlockPos pos, BlockState state) {
		expected.put(pos.asLong(), Block.getId(state));
		normal.put(pos.asLong(), Block.getId(state));
		changed.remove(pos.asLong());
	}

	/**
	 * Looks over one section of what's around it (called every few ticks, round-robin over the
	 * sections in sight range), learning new places and noticing changes in known ones.
	 */
	public void look(ServerLevel level, Vec3 eye, long now) {
		List<Long> near = new ArrayList<>();
		SectionPos center = SectionPos.of(BlockPos.containing(eye));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					near.add(SectionPos.asLong(center.x() + dx, center.y() + dy, center.z() + dz));
				}
			}
		}
		long sectionKey = near.get(sectionCursor++ % near.size());
		SectionPos section = SectionPos.of(sectionKey);
		boolean learning = !known.containsKey(sectionKey) || now - known.get(sectionKey) < SETTLE;
		known.putIfAbsent(sectionKey, now);

		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					p.set(section.minBlockX() + x, section.minBlockY() + y, section.minBlockZ() + z);
					if (!level.isLoaded(p)) {
						continue;
					}
					long key = p.asLong();
					BlockState state = level.getBlockState(p);
					Integer before = normal.get(key);
					boolean isInteresting = interesting(state);
					if (before == null && !isInteresting) {
						continue;
					}
					int id = Block.getId(state);
					if (learning) {
						if (isInteresting) {
							normal.put(key, id);
						}
						continue;
					}
					if (Vec3.atCenterOf(p).distanceToSqr(eye) > SIGHT * SIGHT || !visible(level, eye, p)) {
						continue;
					}
					if (before == null) {
						normal.put(key, id);
						report(p, null, now, state, null);
						continue;
					}
					if (before == id) {
						changed.remove(key);
						continue;
					}
					Integer mine = expected.get(key);
					if ((mine != null && mine == id) || ignoring(p, now)) {
						normal.put(key, id);
						changed.remove(key);
						continue;
					}
					long[] since = changed.get(key);
					if (since == null || since[0] != id) {
						changed.put(key, new long[] {id, now});
						report(p, null, now, state, Block.stateById(before));
					} else if (now - since[1] > ACCEPT) {
						if (isInteresting) {
							normal.put(key, id);
						} else {
							normal.remove(key);
						}
						changed.remove(key);
					}
				}
			}
		}
	}

	/** An item it put there itself (remains, arrows it pulled out): nothing to look into. */
	public void ownItem(ItemEntity item) {
		itemsSeen.add(item.getUUID());
	}

	/** Notices items lying around that weren't there before (things left behind). */
	public void lookForItems(ServerLevel level, Vec3 eye, long now) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, eye).inflate(SIGHT))) {
			if (itemsSeen.contains(item.getUUID())) {
				continue;
			}
			// Only once it has actually seen the item: an item in a place it has known for a while is
			// something left behind; one that turns up where it's still learning is just part of it.
			Vec3 at = item.position().add(0, 0.25, 0);
			if (DEBUG) {
				Long known0 = known.get(SectionPos.asLong(item.blockPosition()));
				rauk.com.Rauk.LOGGER.info("item {} at {} visible {} known {}", item.getItem(), at, visible(level, eye, at),
						known0 == null ? "never" : (now - known0));
			}
			if (!visible(level, eye, at)) {
				continue;
			}
			itemsSeen.add(item.getUUID());
			Long since = known.get(SectionPos.asLong(item.blockPosition()));
			if (since != null && now - since > SETTLE) {
				report(item.blockPosition(), Kind.ITEM_LEFT, now, level.getBlockState(item.blockPosition()), null);
			}
		}
		if (itemsSeen.size() > 512) {
			itemsSeen.clear();
		}
	}

	private void report(BlockPos p, @Nullable Kind kind, long now, BlockState state, @Nullable BlockState before) {
		if (kind == null) {
			kind = classify(state, before);
		}
		if (kind == Kind.LIGHT_ON || kind == Kind.LIGHT_OFF) {
			// Lights going on or off all over at once is the power, not someone at a switch: the
			// first one or two are worth a look, the rest aren't.
			Kind k = kind;
			if (anomalies.stream().filter(a -> a.kind() == k && now - a.time() < 100).count() >= 2) {
				return;
			}
		}
		BlockPos at = p.immutable();
		anomalies.removeIf(a -> a.pos().equals(at));
		anomalies.add(new Anomaly(at, kind, now));
		if (anomalies.size() > 32) {
			anomalies.remove(0);
		}
	}

	private static Kind classify(BlockState state, @Nullable BlockState before) {
		if (before != null && !interesting(state)) {
			return before.getLightEmission() > 0 ? Kind.LIGHT_GONE : Kind.DOOR_CLOSED;
		}
		if (state.getBlock() instanceof LeverBlock) {
			return Kind.LEVER;
		}
		if (state.hasProperty(BlockStateProperties.OPEN)) {
			return state.getValue(BlockStateProperties.OPEN) ? Kind.DOOR_OPENED : Kind.DOOR_CLOSED;
		}
		if (state.hasProperty(BlockStateProperties.LIT)) {
			return state.getValue(BlockStateProperties.LIT) ? Kind.LIGHT_ON : Kind.LIGHT_OFF;
		}
		return state.getLightEmission() > 0 ? Kind.LIGHT_ADDED : Kind.LIGHT_GONE;
	}

	private static boolean visible(ServerLevel level, Vec3 eye, Vec3 target) {
		var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static boolean visible(ServerLevel level, Vec3 eye, BlockPos p) {
		Vec3 target = Vec3.atCenterOf(p);
		var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
	}

	/** The oldest anomaly it hasn't been to look at yet. */
	@Nullable
	public Anomaly next() {
		return anomalies.isEmpty() ? null : anomalies.get(0);
	}

	/** It has looked at this place: stop wanting to. */
	public void investigated(BlockPos pos) {
		for (Iterator<Anomaly> it = anomalies.iterator(); it.hasNext();) {
			if (it.next().pos().distSqr(pos) < 9) {
				it.remove();
			}
		}
	}

	public int knownCount() {
		return normal.size();
	}

	/**
	 * It knows its own hotel: learns every door, lever and light within range at once, seen or not,
	 * as how things normally are. Done when it first finds itself somewhere.
	 */
	public void survey(ServerLevel level, BlockPos center, int radius, long now) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius / 2; y <= radius / 2; y++) {
				for (int z = -radius; z <= radius; z++) {
					p.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (!level.isLoaded(p)) {
						continue;
					}
					BlockState state = level.getBlockState(p);
					if (interesting(state)) {
						normal.put(p.asLong(), Block.getId(state));
					}
					if ((x & 15) == 0 && (y & 15) == 0 && (z & 15) == 0) {
						known.putIfAbsent(SectionPos.asLong(p), now - SETTLE);
					}
				}
			}
		}
		for (int sx = -radius; sx <= radius; sx += 16) {
			for (int sy = -radius / 2; sy <= radius / 2; sy += 16) {
				for (int sz = -radius; sz <= radius; sz += 16) {
					known.putIfAbsent(SectionPos.asLong(center.offset(sx, sy, sz)), now - SETTLE);
				}
			}
		}
	}

	/** Changes near here until then were its own doing (lights it switched): take them as normal. */
	public void ignoreChangesNear(BlockPos pos, int radius, long until) {
		ignored.add(new long[] {pos.asLong(), radius, until});
	}

	private boolean ignoring(BlockPos p, long now) {
		ignored.removeIf(e -> e[2] < now);
		for (long[] e : ignored) {
			if (BlockPos.of(e[0]).distSqr(p) <= e[1] * e[1]) {
				return true;
			}
		}
		return false;
	}

	/** Everything it knows of that matches, within range of a point. */
	public List<BlockPos> known(BlockPos near, double range, java.util.function.Predicate<BlockState> test) {
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<Long, Integer> e : normal.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			if (p.distSqr(near) <= range * range && test.test(Block.stateById(e.getValue()))) {
				out.add(p);
			}
		}
		return out;
	}

	/** How it remembers a block, or null if it doesn't know it. */
	@Nullable
	public BlockState remembered(BlockPos pos) {
		Integer id = normal.get(pos.asLong());
		return id == null ? null : Block.stateById(id);
	}

	// --- saving ---------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		long[] keys = new long[normal.size()];
		int[] ids = new int[normal.size()];
		int i = 0;
		for (Map.Entry<Long, Integer> e : normal.entrySet()) {
			keys[i] = e.getKey();
			ids[i++] = e.getValue();
		}
		tag.putLongArray("blocks", keys);
		tag.putIntArray("states", ids);
		long[] sections = new long[known.size() * 2];
		i = 0;
		for (Map.Entry<Long, Long> e : known.entrySet()) {
			sections[i++] = e.getKey();
			sections[i++] = e.getValue();
		}
		tag.putLongArray("sections", sections);
		ListTag list = new ListTag();
		for (Anomaly a : anomalies) {
			CompoundTag c = new CompoundTag();
			c.putLong("pos", a.pos().asLong());
			c.putString("kind", a.kind().name());
			c.putLong("time", a.time());
			list.add(c);
		}
		tag.put("anomalies", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		normal.clear();
		known.clear();
		anomalies.clear();
		long[] keys = tag.getLongArray("blocks");
		int[] ids = tag.getIntArray("states");
		for (int i = 0; i < Math.min(keys.length, ids.length); i++) {
			normal.put(keys[i], ids[i]);
		}
		long[] sections = tag.getLongArray("sections");
		for (int i = 0; i + 1 < sections.length; i += 2) {
			known.put(sections[i], sections[i + 1]);
		}
		for (Tag element : tag.getList("anomalies", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) element;
			try {
				anomalies.add(new Anomaly(BlockPos.of(c.getLong("pos")), Kind.valueOf(c.getString("kind")), c.getLong("time")));
			} catch (IllegalArgumentException ignored) {
				// An anomaly kind from an older version: forget it.
			}
		}
	}
}
