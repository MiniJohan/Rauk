package rauk.com.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the world knows about its hotel, saved with the Overworld: where it stands (found from the
 * world's structure, or put there by the test command), whether it has been gone over once after
 * generation, whether the coal chute has fallen in, and whether the power is on; and how far the
 * players have got (hotel design 4): what has been done (a crank fitted, a key turned, a puzzle
 * solved), and which locked doors have been opened with their keys.
 */
public final class HotelData extends SavedData {
	private static final String NAME = "rauk_hotel";

	private HotelFrame frame;
	private boolean generated, repaired, chuteDown, power;
	private long lastOccupied;
	/** Where each lift's car is: an index into its floors. */
	private final java.util.Map<String, Integer> lifts = new java.util.HashMap<>();
	/** What has been done, by name (see {@link HotelProgress}). */
	private final java.util.Set<String> done = new java.util.TreeSet<>();
	/** The locked doors that have been opened with their keys: both halves, as plan cells. */
	private final java.util.Set<Long> unlocked = new java.util.HashSet<>();
	/** When the lockdown was lifted (the finale's lever), in game time. */
	private long releasedAt;

	public static HotelData get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(HotelData::new, HotelData::load, null), NAME);
	}

	/** Where the hotel is, or null if this world has none (yet). */
	public HotelFrame frame() {
		return frame;
	}

	/** A hotel found or built: generated ones are gone over once when someone first comes near. */
	public void set(HotelFrame frame, boolean generated) {
		this.frame = frame;
		this.generated = generated;
		this.repaired = !generated;
		this.chuteDown = false;
		this.lifts.clear();
		// A new hotel starts from the beginning.
		this.done.clear();
		this.unlocked.clear();
		this.releasedAt = 0;
		setDirty();
	}

	/** Whether something has been done (a crank fitted, a puzzle solved). */
	public boolean done(String what) {
		return done.contains(what);
	}

	public void setDone(String what, boolean yes) {
		if (yes ? done.add(what) : done.remove(what)) {
			setDirty();
		}
	}

	public java.util.Set<String> allDone() {
		return java.util.Collections.unmodifiableSet(done);
	}

	/** The plan cells of the locked doors opened with their keys. */
	public java.util.Set<Long> unlocked() {
		return java.util.Collections.unmodifiableSet(unlocked);
	}

	public long releasedAt() {
		return releasedAt;
	}

	public void setReleasedAt(long time) {
		releasedAt = time;
		setDirty();
	}

	public void unlock(long cell) {
		if (unlocked.add(cell)) {
			setDirty();
		}
	}

	/** Back to the start: nothing done (the doors in the world are as they are). */
	public void resetProgress() {
		done.clear();
		unlocked.clear();
		releasedAt = 0;
		setDirty();
	}

	public int liftFloor(String lift, int home) {
		return lifts.getOrDefault(lift, home);
	}

	public void resetLifts() {
		lifts.clear();
		setDirty();
	}

	public void setLiftFloor(String lift, int floor) {
		lifts.put(lift, floor);
		setDirty();
	}

	public boolean needsRepair() {
		return frame != null && generated && !repaired;
	}

	public void repaired() {
		repaired = true;
		setDirty();
	}

	public boolean chuteDown() {
		return chuteDown;
	}

	public void setChuteDown(boolean down) {
		chuteDown = down;
		setDirty();
	}

	/** Whether the hotel has power (the generator running, puzzle 2): its electric lights work. */
	public boolean power() {
		return power;
	}

	public void setPower(boolean on) {
		power = on;
		setDirty();
	}

	public long lastOccupied() {
		return lastOccupied;
	}

	public void occupied(long time) {
		lastOccupied = time;
	}

	private static HotelData load(CompoundTag tag, HolderLookup.Provider registries) {
		HotelData data = new HotelData();
		if (tag.contains("Origin")) {
			data.frame = new HotelFrame(BlockPos.of(tag.getLong("Origin")), Rotation.values()[tag.getInt("Rot")]);
		}
		data.generated = tag.getBoolean("Generated");
		data.repaired = tag.getBoolean("Repaired");
		data.chuteDown = tag.getBoolean("ChuteDown");
		data.power = tag.getBoolean("Power");
		CompoundTag lifts = tag.getCompound("Lifts");
		for (String k : lifts.getAllKeys()) {
			data.lifts.put(k, lifts.getInt(k));
		}
		data.lastOccupied = tag.getLong("LastOccupied");
		for (Tag t : tag.getList("Done", Tag.TAG_STRING)) {
			data.done.add(t.getAsString());
		}
		for (long cell : tag.getLongArray("Unlocked")) {
			data.unlocked.add(cell);
		}
		data.releasedAt = tag.getLong("ReleasedAt");
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		if (frame != null) {
			tag.putLong("Origin", frame.origin().asLong());
			tag.putInt("Rot", frame.rotation().ordinal());
		}
		tag.putBoolean("Generated", generated);
		tag.putBoolean("Repaired", repaired);
		tag.putBoolean("ChuteDown", chuteDown);
		tag.putBoolean("Power", power);
		CompoundTag l = new CompoundTag();
		lifts.forEach(l::putInt);
		tag.put("Lifts", l);
		tag.putLong("LastOccupied", lastOccupied);
		ListTag d = new ListTag();
		done.forEach(s -> d.add(StringTag.valueOf(s)));
		tag.put("Done", d);
		tag.put("Unlocked", new LongArrayTag(unlocked.stream().mapToLong(Long::longValue).toArray()));
		tag.putLong("ReleasedAt", releasedAt);
		return tag;
	}
}
