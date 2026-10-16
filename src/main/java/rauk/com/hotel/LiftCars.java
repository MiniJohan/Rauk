package rauk.com.hotel;

import java.util.ArrayList;
import java.util.List;

import rauk.com.hotel.HotelPlan.Level;

/**
 * The hotel's lifts (hotel design 3.2 and 14), as the plan has them: each a shaft three by three, a
 * gate onto its landing on every floor it serves, a call button beside each gate, and one car.
 * The car is a floor, a roof, a lamp and the operator's lever, standing in the shaft at one floor
 * at a time; the rest of the shaft is open, and Rauk's. Plain Java, no game classes.
 */
public final class LiftCars {
	private LiftCars() {
	}

	/**
	 * A lift: its shaft's north-west corner (it's three by three), the floors it serves and the one
	 * its car starts at, where its gates and call buttons are and which way they face, and what its
	 * car is made of.
	 */
	public record Lift(String id, String name, int x0, int z0, List<Level> floors, Level home, int gateX, int gateZ, String gateFacing,
			int controlX, int controlZ, String controlFacing, int callX, int callZ, String callFacing, String floor, String roof,
			String lamp, int lampX, int lampZ) {
		/** A floor's place in the list, or -1. */
		public int index(Level lv) {
			return floors.indexOf(lv);
		}
	}

	/** The service lift in the staff core: B2 to the penthouse, its car of iron. Its car waits in the pit. */
	public static final Lift SERVICE = new Lift("service", "Service lift", 39, 1,
			List.of(Level.B2, Level.B1, Level.L1, Level.L2, Level.L3, Level.L4), Level.B2, 40, 4, "north", 39, 2, "east", 41, 5, "south",
			"rauk:lift_floor", "rauk:lift_roof", "caged_bulb", 40, 2);
	/** The guest lift in its brass cage in the atrium: 1 to 3, its car parquet-floored. */
	public static final Lift GUEST = new Lift("guest", "Guest lift", 43, 21, List.of(Level.L1, Level.L2, Level.L3), Level.L1, 46, 22, "west",
			43, 22, "east", 47, 21, "east", "rauk:parquet", "rauk:brass", "pendant_lantern", 44, 22);
	/**
	 * The rotunda's lift (hotel design 14), in its brass cage up the middle of the round stair: 1
	 * to 3, its gate onto the stair's landing on the south side.
	 */
	public static final Lift ROTUNDA = new Lift("rotunda", "Rotunda lift", 38, 107, List.of(Level.L1, Level.L2, Level.L3), Level.L1, 39, 110,
			"north", 38, 108, "east", 40, 111, "south", "rauk:parquet", "rauk:brass", "pendant_lantern", 39, 108);
	public static final List<Lift> ALL = List.of(SERVICE, GUEST, ROTUNDA);

	/** A block of a car: where, relative to its floor (dy 0 is the floor it's standing at), and what. */
	public record Part(int x, int dy, int z, String block) {
	}

	/** The car's blocks, standing at a floor; its lamp lit or not. */
	public static List<Part> car(Lift lift, boolean lit) {
		List<Part> out = new ArrayList<>();
		for (int x = lift.x0(); x < lift.x0() + 3; x++) {
			for (int z = lift.z0(); z < lift.z0() + 3; z++) {
				out.add(new Part(x, -1, z, lift.floor()));
				out.add(new Part(x, 3, z, lift.roof()));
			}
		}
		out.add(new Part(lift.lampX(), 2, lift.lampZ(), "rauk:" + lift.lamp() + "[lit=" + lit + "]"));
		out.add(new Part(lift.controlX(), 1, lift.controlZ(), "rauk:lift_control[facing=" + lift.controlFacing() + "]"));
		return out;
	}

	/** Whether a plan cell is a lift's gate, and on which of its floors: the lift, or null. */
	public static Lift gateAt(int x, int y, int z) {
		for (Lift l : ALL) {
			if (l.gateX() == x && l.gateZ() == z && floorAt(l, y) != null) {
				return l;
			}
		}
		return null;
	}

	/** The floor of a lift whose doorway (its two blocks) or landing takes in a height, or null. */
	public static Level floorAt(Lift lift, int y) {
		for (Level lv : lift.floors()) {
			if (y >= lv.base && y <= lv.base + 1) {
				return lv;
			}
		}
		return null;
	}
}
