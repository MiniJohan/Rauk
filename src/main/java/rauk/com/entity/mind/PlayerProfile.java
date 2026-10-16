package rauk.com.entity.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * What Rauk learns about one player over time (design doc 6.5), saved and fading slowly so old
 * lessons wear off. This step fills in what its senses can teach it: where the player keeps
 * coming back to (their room), the corridors their scent uses most (their routes), whether they
 * rely on light, and which startle tricks it has got used to. What it learns from fights is added
 * with combat.
 */
public final class PlayerProfile {
	/** Fraction of every memory kept per in-game minute (1200 ticks): fades over an hour or two. */
	private static final double KEEP_PER_MINUTE = 0.985;
	private static final int ROOM_CELL = 6, ROUTE_CELL = 3;

	public final UUID player;
	/** Where the player was found, by 6-block cell: the most weight is "their room". */
	private final Map<Long, Double> places = new HashMap<>();
	/** Where their scent trail runs, by 3-block cell: the busiest cells are "their routes". */
	private final Map<Long, Double> routes = new HashMap<>();
	/** Where they ran to when it chased them and lost sight of them, by 3-block cell: their bolt-holes. */
	private final Map<Long, Double> boltHoles = new HashMap<>();
	/** Average light level where it has found them, and how many lights they've switched on. */
	public double lightWhereFound = 7;
	public double lightsSwitched;
	/** How used to each startle trick it is: after a couple it stops startling. */
	private final Map<String, Double> habituation = new HashMap<>();
	private long lastFade;
	/** Damage this player has done to it, how much of it from range, and how often it got away when grabbed. */
	private int escapes;

	public PlayerProfile(UUID player) {
		this.player = player;
	}

	public void foundAt(BlockPos pos, int light) {
		add(places, cell(pos, ROOM_CELL), 1);
		lightWhereFound += (light - lightWhereFound) * 0.05;
	}

	public void trailThrough(BlockPos pos) {
		add(routes, cell(pos, ROUTE_CELL), 0.2);
	}

	public void switchedLight() {
		lightsSwitched += 1;
	}

	/** It heard this trick again. Returns whether it still startles: only the first time or two. */
	public boolean startledBy(String trick) {
		double seen = habituation.getOrDefault(trick, 0.0);
		habituation.put(trick, seen + 1);
		return seen < 2;
	}

	public void escaped() {
		escapes++;
	}

	/** How many times they have broken free of its grip. It holds a grudge. */
	public int escapes() {
		return escapes;
	}

	/** They ran from it and it lost them here, heading this way: a bolt-hole it will remember. */
	public void fledTo(BlockPos pos) {
		add(boltHoles, cell(pos, ROUTE_CELL), 1);
	}

	/** A place it has found them before (test command): as if it had found them there this often. */
	public void addHaunt(BlockPos pos, double weight) {
		add(places, cell(pos, ROOM_CELL), weight);
	}

	/** A weighted place, its centre and how much it counts. */
	public record Place(BlockPos at, double weight) {
	}

	/** The places it has found them in, near a point: their haunts, heaviest first. */
	public java.util.List<Place> haunts(BlockPos near, double range) {
		return list(places, ROOM_CELL, near, range);
	}

	/** The places they ran to when it lost them, near a point. */
	public java.util.List<Place> boltHoles(BlockPos near, double range) {
		return list(boltHoles, ROUTE_CELL, near, range);
	}

	private static java.util.List<Place> list(Map<Long, Double> map, int size, BlockPos near, double range) {
		java.util.List<Place> out = new java.util.ArrayList<>();
		for (Map.Entry<Long, Double> e : map.entrySet()) {
			BlockPos c = BlockPos.of(e.getKey());
			BlockPos at = new BlockPos(c.getX() * size + size / 2, c.getY() * size, c.getZ() * size + size / 2);
			if (at.distSqr(near) <= range * range) {
				out.add(new Place(at, e.getValue()));
			}
		}
		out.sort(java.util.Comparator.comparingDouble(Place::weight).reversed());
		return out;
	}

	/** Ticks the player has to break free when grabbed: 2 seconds, 0.25 less per escape, at least 1.25. */
	public int struggleWindow() {
		return Math.max(25, 40 - 5 * escapes);
	}

	/** Lets everything fade by the time passed. */
	public void fade(long now) {
		if (lastFade == 0) {
			lastFade = now;
			return;
		}
		double minutes = (now - lastFade) / 1200.0;
		if (minutes < 1) {
			return;
		}
		double keep = Math.pow(KEEP_PER_MINUTE, minutes);
		scale(places, keep);
		scale(routes, keep);
		scale(boltHoles, keep);
		lightsSwitched *= keep;
		habituation.replaceAll((k, v) -> v * keep);
		lastFade = now;
	}

	/** The centre of the cell it has found them in most: their room, or null if it doesn't know yet. */
	@Nullable
	public BlockPos room() {
		// A place counts as theirs after the equivalent of half a minute or so of finding them there.
		return busiest(places, ROOM_CELL, 150);
	}

	@Nullable
	public BlockPos busiestRoute() {
		return busiest(routes, ROUTE_CELL, 2);
	}

	public boolean reliesOnLight() {
		return lightWhereFound > 10 || lightsSwitched > 2;
	}

	private static long cell(BlockPos pos, int size) {
		return BlockPos.asLong(Math.floorDiv(pos.getX(), size), Math.floorDiv(pos.getY(), size), Math.floorDiv(pos.getZ(), size));
	}

	private static void add(Map<Long, Double> map, long key, double amount) {
		map.merge(key, amount, Double::sum);
	}

	private static void scale(Map<Long, Double> map, double keep) {
		map.replaceAll((k, v) -> v * keep);
		map.values().removeIf(v -> v < 0.05);
	}

	@Nullable
	private static BlockPos busiest(Map<Long, Double> map, int size, double atLeast) {
		long best = 0;
		double weight = atLeast;
		for (Map.Entry<Long, Double> e : map.entrySet()) {
			if (e.getValue() > weight) {
				weight = e.getValue();
				best = e.getKey();
			}
		}
		if (weight == atLeast) {
			return null;
		}
		BlockPos c = BlockPos.of(best);
		return new BlockPos(c.getX() * size + size / 2, c.getY() * size + size / 2, c.getZ() * size + size / 2);
	}

	// --- saving ---------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("player", player);
		tag.put("places", saveMap(places));
		tag.put("routes", saveMap(routes));
		tag.put("boltHoles", saveMap(boltHoles));
		tag.putDouble("light", lightWhereFound);
		tag.putDouble("lightsSwitched", lightsSwitched);
		CompoundTag h = new CompoundTag();
		habituation.forEach(h::putDouble);
		tag.put("habituation", h);
		tag.putLong("lastFade", lastFade);
		tag.putInt("escapes", escapes);
		return tag;
	}

	public static PlayerProfile load(CompoundTag tag) {
		PlayerProfile p = new PlayerProfile(tag.getUUID("player"));
		loadMap(tag.getCompound("places"), p.places);
		loadMap(tag.getCompound("routes"), p.routes);
		loadMap(tag.getCompound("boltHoles"), p.boltHoles);
		p.lightWhereFound = tag.getDouble("light");
		p.lightsSwitched = tag.getDouble("lightsSwitched");
		CompoundTag h = tag.getCompound("habituation");
		for (String k : h.getAllKeys()) {
			p.habituation.put(k, h.getDouble(k));
		}
		p.lastFade = tag.getLong("lastFade");
		p.escapes = tag.getInt("escapes");
		return p;
	}

	private static CompoundTag saveMap(Map<Long, Double> map) {
		CompoundTag tag = new CompoundTag();
		long[] keys = new long[map.size()];
		long[] values = new long[map.size()];
		int i = 0;
		for (Map.Entry<Long, Double> e : map.entrySet()) {
			keys[i] = e.getKey();
			values[i++] = Double.doubleToLongBits(e.getValue());
		}
		tag.putLongArray("keys", keys);
		tag.putLongArray("values", values);
		return tag;
	}

	private static void loadMap(CompoundTag tag, Map<Long, Double> map) {
		long[] keys = tag.getLongArray("keys");
		long[] values = tag.getLongArray("values");
		for (int i = 0; i < Math.min(keys.length, values.length); i++) {
			map.put(keys[i], Double.longBitsToDouble(values[i]));
		}
	}
}
