package rauk.com.entity.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The scent trails every player leaves behind (design doc 3): a point about every block they
 * walk, kept for about two in-game days, stronger while they are hurt. Walking through water
 * leaves no scent, which breaks the trail. Saved with the world, one set per dimension.
 */
public final class ScentData extends SavedData {
	/** How long a trail lasts: two in-game days. */
	public static final long LIFETIME = 48_000;
	private static final int CAPACITY = 4096;
	private static final String NAME = "rauk_scent";

	private final Map<UUID, Trail> trails = new HashMap<>();

	/** One player's trail: a ring of points, oldest overwritten first. */
	public static final class Trail {
		final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY];
		final long[] time = new long[CAPACITY];
		final byte[] strength = new byte[CAPACITY];
		/** How many points were ever laid; the newest is at (count - 1) % CAPACITY. */
		long count;
		Vec3 last;

		public long count() {
			return count;
		}

		/** Whether point number i (in laying order) still exists. */
		public boolean has(long i) {
			return i >= 0 && i < count && i >= count - CAPACITY;
		}

		public Vec3 point(long i) {
			int k = (int) (i % CAPACITY);
			return new Vec3(x[k], y[k], z[k]);
		}

		public long time(long i) {
			return time[(int) (i % CAPACITY)];
		}

		/** 0 for a break in the trail (water), 1 normal, 2 bleeding. */
		public int strength(long i) {
			return strength[(int) (i % CAPACITY)];
		}

		void add(Vec3 p, long t, int s) {
			int k = (int) (count % CAPACITY);
			x[k] = (float) p.x;
			y[k] = (float) p.y;
			z[k] = (float) p.z;
			time[k] = t;
			strength[k] = (byte) s;
			count++;
			last = p;
		}
	}

	public static ScentData get(ServerLevel level) {
		// The saved file carries the current data version, so this fixer type never changes anything;
		// vanilla just needs one.
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(ScentData::new, ScentData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES), NAME);
	}

	/** Lays scent for a player or other prey if it has moved on from its last point. */
	public void lay(LivingEntity player, long now) {
		// Feet on the ground, wading or climbing; not flying or falling.
		if (player.isSpectator() || (player instanceof Player p && p.getAbilities().flying) || player.isFallFlying()
				|| player.fallDistance > 1.5f) {
			return;
		}
		Trail trail = trails.computeIfAbsent(player.getUUID(), u -> new Trail());
		Vec3 at = player.position().add(0, 0.2, 0);
		if (trail.last != null && trail.last.distanceToSqr(at) < 0.9 * 0.9) {
			return;
		}
		int strength = player.isInWater() ? 0 : player.getHealth() < player.getMaxHealth() * 0.5f ? 2 : 1;
		trail.add(at, now, strength);
		if (trail.count % 64 == 0) {
			setDirty();
		}
	}

	@Nullable
	public Trail trail(UUID player) {
		return trails.get(player);
	}

	public Map<UUID, Trail> trails() {
		return trails;
	}

	/** A point on a trail: whose, which one, where, and how old in ticks. */
	public record Hit(UUID player, long index, Vec3 position, long age) {
	}

	/**
	 * The freshest scent within reach of a point, from anyone's trail: what Rauk smells with its
	 * nose at that point.
	 */
	@Nullable
	public Hit smell(Vec3 nose, double reach, long now) {
		Hit best = null;
		for (Map.Entry<UUID, Trail> e : trails.entrySet()) {
			Trail t = e.getValue();
			for (long i = t.count - 1; t.has(i); i--) {
				long age = now - t.time(i);
				if (age > LIFETIME) {
					break;
				}
				if (best != null && age >= best.age) {
					break;
				}
				if (t.strength(i) == 0) {
					continue;
				}
				Vec3 p = t.point(i);
				if (p.distanceToSqr(nose) <= reach * reach) {
					best = new Hit(e.getKey(), i, p, age);
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Following a trail: the point a few steps newer than this one, as long as the trail is
	 * unbroken. Returns null where it ends or breaks (water).
	 */
	@Nullable
	public Hit follow(Hit from, int steps, long now) {
		Trail t = trails.get(from.player());
		if (t == null) {
			return null;
		}
		long i = from.index();
		for (int k = 0; k < steps && t.has(i + 1); k++) {
			if (t.strength(i + 1) == 0) {
				break;
			}
			i++;
		}
		return i == from.index() ? null : new Hit(from.player(), i, t.point(i), now - t.time(i));
	}

	// --- saving ---------------------------------------------------------------------------------

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Trail> e : trails.entrySet()) {
			Trail t = e.getValue();
			long first = Math.max(0, t.count - CAPACITY);
			int n = (int) (t.count - first);
			float[] xyz = new float[n * 3];
			long[] times = new long[n];
			byte[] strengths = new byte[n];
			for (int j = 0; j < n; j++) {
				Vec3 p = t.point(first + j);
				xyz[3 * j] = (float) p.x;
				xyz[3 * j + 1] = (float) p.y;
				xyz[3 * j + 2] = (float) p.z;
				times[j] = t.time(first + j);
				strengths[j] = (byte) t.strength(first + j);
			}
			CompoundTag c = new CompoundTag();
			c.putUUID("player", e.getKey());
			int[] packed = new int[xyz.length];
			for (int j = 0; j < xyz.length; j++) {
				packed[j] = Float.floatToIntBits(xyz[j]);
			}
			c.putIntArray("xyz", packed);
			c.putLongArray("time", times);
			c.putByteArray("strength", strengths);
			list.add(c);
		}
		tag.put("trails", list);
		return tag;
	}

	private static ScentData load(CompoundTag tag, HolderLookup.Provider registries) {
		ScentData data = new ScentData();
		for (Tag element : tag.getList("trails", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) element;
			int[] packed = c.getIntArray("xyz");
			long[] times = c.getLongArray("time");
			byte[] strengths = c.getByteArray("strength");
			Trail t = new Trail();
			for (int j = 0; j < times.length && 3 * j + 2 < packed.length; j++) {
				t.add(new Vec3(Float.intBitsToFloat(packed[3 * j]), Float.intBitsToFloat(packed[3 * j + 1]),
						Float.intBitsToFloat(packed[3 * j + 2])), times[j], j < strengths.length ? strengths[j] : 1);
			}
			data.trails.put(c.getUUID("player"), t);
		}
		return data;
	}
}
