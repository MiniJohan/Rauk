package rauk.com.hotel;

import static rauk.com.hotel.HotelFurniture.DOWN;
import static rauk.com.hotel.HotelFurniture.EAST;
import static rauk.com.hotel.HotelFurniture.NORTH;
import static rauk.com.hotel.HotelFurniture.SOUTH;
import static rauk.com.hotel.HotelFurniture.UP;
import static rauk.com.hotel.HotelFurniture.WEST;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rauk.com.hotel.HotelFurniture.Box;
import rauk.com.hotel.HotelFurniture.Form;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelFurniture.Look;
import rauk.com.hotel.HotelFurniture.M;
import rauk.com.hotel.HotelFurniture.Mount;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.HotelMaterials.Feel;

/**
 * Every piece of the hotel's furniture (hotel design 12), drawn as boxes. Pieces that share a
 * shape share its drawing and differ in what they're covered with: the same chair in red leather,
 * or torn, or charred. Things that stand on tables (lamps, place settings, a ledger) are pieces of
 * their own, set on the block above the table.
 */
final class FurnitureCatalog {
	static final List<Piece> ALL;

	static {
		List<Piece> all = new ArrayList<>();
		seating(all);
		tables(all);
		counters(all);
		storage(all);
		beds(all);
		lights(all);
		walls(all);
		tabletop(all);
		carried(all);
		things(all);
		reshaped(all);
		service(all);
		debris(all);
		ALL = Collections.unmodifiableList(all);
	}

	private FurnitureCatalog() {
	}

	private static Piece piece(List<Piece> all, String id, String name, Form form, String group) {
		Piece p = new Piece(id, name, form).group(group);
		all.add(p);
		return p;
	}

	// --- drawing helpers ---------------------------------------------------------------------------

	/** Four legs, one at each corner of a box, inset from its edges. */
	private static void legs(M m, double x0, double z0, double x1, double z1, double size, double height, String role) {
		for (double x : new double[] {x0, x1 - size}) {
			for (double z : new double[] {z0, z1 - size}) {
				m.box(x, 0, z, x + size, height, z + size, role);
			}
		}
	}

	/** Something round, as two crossed boxes: from above an eight-sided shape. */
	private static void round(M m, double cx, double cz, double y0, double y1, double r, String role) {
		round(m, cx, cz, y0, y1, r, role, false);
	}

	private static void round(M m, double cx, double cz, double y0, double y1, double r, String role, boolean glow) {
		m.box(cx - r, y0, cz - r * 0.6, cx + r, y1, cz + r * 0.6, role);
		if (glow) {
			m.glow();
		}
		m.box(cx - r * 0.6, y0, cz - r, cx + r * 0.6, y1, cz + r, role);
		if (glow) {
			m.glow();
		}
	}

	/**
	 * Sixteen boxes in a ring about (cx, cz): each drawn at one of the four sides and turned about
	 * the middle to its place.
	 */
	private static void ring(M m, double cx, double y, double cz, double r, double thick, double h, double len, String role, boolean glow) {
		double half = len / 2;
		for (int side = 0; side < 4; side++) {
			for (double turn : new double[] {-45, -22.5, 0, 22.5}) {
				switch (side) {
					case 0 -> m.box(cx + r - thick, y, cz - half, cx + r, y + h, cz + half, role);
					case 1 -> m.box(cx - half, y, cz + r - thick, cx + half, y + h, cz + r, role);
					case 2 -> m.box(cx - r, y, cz - half, cx - r + thick, y + h, cz + half, role);
					default -> m.box(cx - half, y, cz - r, cx + half, y + h, cz - r + thick, role);
				}
				if (glow) {
					m.glow();
				}
				if (turn != 0) {
					m.turn('y', turn, cx, y, cz);
				}
			}
		}
	}

	// --- seating -----------------------------------------------------------------------------------

	private static void seating(List<Piece> all) {
		piece(all, "club_chair", "Club Chair", Form.SINGLE, "seating").feel(Feel.CLOTH).model(l -> clubChair())
				.skin("leather", "f_leather_oxblood", "tufted", "f_leather_oxblood_tufted", "wood", "f_mahogany");
		piece(all, "green_club_chair", "Green Club Chair", Form.SINGLE, "seating").feel(Feel.CLOTH).model(l -> clubChair())
				.skin("leather", "f_leather_green", "tufted", "f_leather_green_tufted", "wood", "f_mahogany");
		piece(all, "torn_club_chair", "Torn Club Chair", Form.SINGLE, "seating").feel(Feel.CLOTH).model(l -> clubChair())
				.skin("leather", "f_leather_oxblood_torn", "tufted", "f_leather_oxblood_torn", "wood", "f_mahogany");
		piece(all, "chesterfield", "Chesterfield Sofa", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::chesterfield)
				.skin("leather", "f_leather_oxblood", "tufted", "f_leather_oxblood_tufted", "wood", "f_mahogany");
		piece(all, "torn_chesterfield", "Torn Chesterfield", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::chesterfield)
				.skin("leather", "f_leather_oxblood_torn", "tufted", "f_leather_oxblood_torn", "wood", "f_mahogany");
		piece(all, "velvet_settee", "Velvet Settee", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::settee)
				.skin("velvet", "f_velvet_red", "gilt", "f_gilt");
		piece(all, "velvet_armchair", "Velvet Armchair", Form.SINGLE, "seating").feel(Feel.CLOTH)
				.model(l -> settee(new Look("single", 0, 0, false))).skin("velvet", "f_velvet_red", "gilt", "f_gilt");
		piece(all, "dining_chair", "Dining Chair", Form.SINGLE, "seating").model(l -> diningChair())
				.skin("wood", "f_mahogany", "velvet", "f_velvet_red");
		piece(all, "overturned_chair", "Overturned Chair", Form.SINGLE, "seating").soft().model(l -> HotelFurniture.tipped(diningChair(), 16))
				.skin("wood", "f_mahogany", "velvet", "f_velvet_red");
		piece(all, "gilt_chair", "Gilt Ballroom Chair", Form.SINGLE, "seating").model(l -> giltChair())
				.skin("gilt", "f_gilt", "velvet", "f_velvet_red");
		piece(all, "bar_stool", "Bar Stool", Form.SINGLE, "seating").noFacing().feel(Feel.METAL).model(l -> barStool())
				.skin("leather", "f_leather_oxblood");
		piece(all, "hall_bench", "Upholstered Bench", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::hallBench)
				.skin("wood", "f_mahogany", "velvet", "f_velvet_red");
		piece(all, "staff_bench", "Staff Bench", Form.RUN, "seating").model(FurnitureCatalog::staffBench).skin("wood", "f_pine");
		piece(all, "kitchen_chair", "Kitchen Chair", Form.SINGLE, "seating").model(l -> kitchenChair()).skin("wood", "f_pine");
		piece(all, "sheeted_armchair", "Armchair Under a Dust Sheet", Form.SINGLE, "seating").feel(Feel.CLOTH).model(l -> sheetedChair())
				.skin("sheet", "f_dust_sheet");
		piece(all, "sheeted_sofa", "Sofa Under a Dust Sheet", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::sheetedSofa)
				.skin("sheet", "f_dust_sheet");
	}

	/** A deep leather club chair: rolled arms, a low buttoned back, on short turned legs. */
	private static List<Box> clubChair() {
		M m = new M();
		legs(m, 2, 2, 14, 14, 2, 2, "wood");
		m.box(1.5, 2, 1.5, 14.5, 6, 15, "leather");
		m.box(4, 6, 2, 12, 8.5, 12.5, "leather");
		m.box(1, 6, 1.5, 4, 10.5, 15, "leather").face(EAST, "tufted");
		m.box(0.5, 9.5, 1, 4.5, 12, 15.5, "leather");
		m.box(12, 6, 1.5, 15, 10.5, 15, "leather").face(WEST, "tufted");
		m.box(11.5, 9.5, 1, 15.5, 12, 15.5, "leather");
		m.box(1.5, 6, 12.5, 14.5, 15, 15.5, "leather").front("tufted");
		m.box(1, 13.5, 12, 15, 16, 16, "leather");
		return m.done();
	}

	/** A Chesterfield, a block of it: buttoned back and arms, arms only at the ends of the row. */
	private static List<Box> chesterfield(Look l) {
		M m = new M();
		boolean lo = l.lowEnd(), hi = l.highEnd();
		m.box(0, 2, 1.5, 16, 6, 15, "leather");
		m.box(lo ? 3 : 0, 6, 2, hi ? 13 : 16, 8.5, 12.5, "leather");
		m.box(0, 6, 12.5, 16, 14, 15.5, "leather").front("tufted");
		m.box(0, 13, 12, 16, 15.5, 16, "leather");
		if (lo) {
			m.box(0, 6, 1.5, 3, 11, 15, "leather").face(EAST, "tufted");
			m.box(-0.5, 10, 1, 3.5, 12.5, 15.5, "leather");
			m.box(1.5, 0, 2.5, 3.5, 2, 4.5, "wood");
			m.box(1.5, 0, 12, 3.5, 2, 14, "wood");
		}
		if (hi) {
			m.box(13, 6, 1.5, 16, 11, 15, "leather").face(WEST, "tufted");
			m.box(12.5, 10, 1, 16.5, 12.5, 15.5, "leather");
			m.box(12.5, 0, 2.5, 14.5, 2, 4.5, "wood");
			m.box(12.5, 0, 12, 14.5, 2, 14, "wood");
		}
		return m.done();
	}

	/** A gilt settee in red velvet, French-style: thin carved legs, open arms at the ends. */
	private static List<Box> settee(Look l) {
		M m = new M();
		boolean lo = l.lowEnd(), hi = l.highEnd();
		m.box(0, 4.5, 2, 16, 8, 13, "velvet");
		m.box(0, 4, 1.5, 16, 5.5, 2.5, "gilt");
		m.box(0, 7.5, 12.5, 16, 17, 14.5, "velvet");
		m.box(0, 17, 12.25, 16, 18, 14.75, "gilt");
		m.box(0, 7.5, 14.5, 16, 17, 15, "gilt").only(SOUTH);
		for (int end = 0; end < 2; end++) {
			if (end == 0 ? !lo : !hi) {
				continue;
			}
			double x = end == 0 ? 0.5 : 14;
			m.box(x, 0, 2, x + 1.5, 4.5, 3.5, "gilt");
			m.box(x, 0, 12.5, x + 1.5, 7.5, 14, "gilt");
			m.box(x, 7.5, 2, x + 1.5, 12, 3.5, "gilt");
			m.box(x, 11, 2, x + 1.5, 12, 13, "gilt");
			m.box(x - 0.25, 10, 4, x + 1.75, 11, 11.5, "velvet");
			m.box(x, 12, 12.5, x + 1.5, 18.5, 14.5, "gilt");
		}
		return m.done();
	}

	/** A mahogany dining chair, a red velvet seat, a carved splat in its back. */
	private static List<Box> diningChair() {
		M m = new M();
		m.box(3, 0, 2.5, 4.5, 8, 4, "wood");
		m.box(11.5, 0, 2.5, 13, 8, 4, "wood");
		m.box(3, 0, 12, 4.5, 19, 13.5, "wood");
		m.box(11.5, 0, 12, 13, 19, 13.5, "wood");
		m.box(4.5, 2.5, 3, 11.5, 3.25, 3.5, "wood");
		m.box(2.5, 7, 2, 13.5, 8.5, 13.5, "wood");
		m.box(3, 8.5, 2.5, 13, 9.5, 12.5, "velvet");
		m.box(3, 17, 12.25, 13, 19, 13.25, "wood");
		m.box(6.5, 9.5, 12.5, 9.5, 17, 13.25, "wood");
		m.box(4.5, 11.5, 12.5, 11.5, 12.25, 13, "wood");
		return m.done();
	}

	/** The ballroom's little gilt chairs: spindly legs, rails across the back, a red cushion. */
	private static List<Box> giltChair() {
		M m = new M();
		legs(m, 3.5, 3.5, 12.5, 12.5, 1, 8, "gilt");
		m.box(3.5, 8, 12, 4.5, 19, 13, "gilt");
		m.box(11.5, 8, 12, 12.5, 19, 13, "gilt");
		m.box(3.5, 7.5, 3.5, 12.5, 8.5, 12.5, "gilt");
		m.box(3.75, 8.5, 3.75, 12.25, 10, 12, "velvet");
		for (double y : new double[] {12, 15, 18}) {
			m.box(4.5, y, 12.25, 11.5, y + 0.75, 12.75, "gilt");
		}
		m.box(4.5, 3, 4, 11.5, 3.6, 4.6, "gilt");
		return m.done();
	}

	private static List<Box> barStool() {
		M m = new M();
		round(m, 8, 8, 0, 1, 3.5, "brass");
		m.box(7.25, 1, 7.25, 8.75, 10, 8.75, "brass");
		m.box(3.5, 4.5, 7.5, 12.5, 5.25, 8.5, "brass");
		m.box(7.5, 4.5, 3.5, 8.5, 5.25, 12.5, "brass");
		round(m, 8, 8, 10, 11, 3.75, "brass");
		round(m, 8, 8, 11, 13, 4, "leather");
		return m.done();
	}

	private static List<Box> hallBench(Look l) {
		M m = new M();
		m.box(0, 6, 3, 16, 9, 13, "velvet");
		m.box(0, 4.5, 3.5, 16, 6, 12.5, "wood");
		for (int end = 0; end < 2; end++) {
			if (end == 0 ? l.lowEnd() : l.highEnd()) {
				double x = end == 0 ? 1 : 13.5;
				m.box(x, 0, 4, x + 1.5, 4.5, 5.5, "wood");
				m.box(x, 0, 10.5, x + 1.5, 4.5, 12, "wood");
			}
		}
		return m.done();
	}

	private static List<Box> staffBench(Look l) {
		M m = new M();
		m.box(0, 7, 4, 16, 8.5, 12, "wood");
		for (int end = 0; end < 2; end++) {
			if (end == 0 ? l.lowEnd() : l.highEnd()) {
				double x = end == 0 ? 1.5 : 13;
				m.box(x, 0, 5, x + 1.5, 7, 11, "wood");
			}
		}
		if (!l.lowEnd() && !l.highEnd()) {
			m.box(7.25, 0, 6, 8.75, 7, 10, "wood");
		}
		m.box(0, 3, 7.5, 16, 4, 8.5, "wood");
		return m.done();
	}

	private static List<Box> kitchenChair() {
		M m = new M();
		legs(m, 3, 3, 13, 13, 1.5, 8, "wood");
		m.box(3, 8, 11.5, 4.5, 18, 13, "wood");
		m.box(11.5, 8, 11.5, 13, 18, 13, "wood");
		m.box(2.5, 7, 2.5, 13.5, 8.5, 13.5, "wood");
		m.box(4.5, 12, 11.75, 11.5, 13, 12.75, "wood");
		m.box(4.5, 16, 11.75, 11.5, 17.5, 12.75, "wood");
		return m.done();
	}

	/** An armchair under a dust sheet: only its shape, the sheet falling in folds to the floor. */
	private static List<Box> sheetedChair() {
		M m = new M();
		m.box(1, 0, 1, 15, 7, 15, "sheet");
		m.box(3.5, 7, 2, 12.5, 8.5, 12, "sheet");
		m.box(0.75, 7, 1, 4.25, 11.5, 15, "sheet");
		m.box(11.75, 7, 1, 15.25, 11.5, 15, "sheet");
		m.box(1.25, 7, 12, 14.75, 15.5, 15.5, "sheet");
		m.box(2, 15.5, 12.5, 14, 16.5, 15, "sheet");
		m.box(0.5, 0, 3, 1, 9, 8, "sheet");
		m.box(15, 0, 6, 15.5, 9, 12, "sheet");
		m.box(5, 0, 0.5, 11, 6, 1, "sheet");
		return m.done();
	}

	private static List<Box> sheetedSofa(Look l) {
		M m = new M();
		boolean lo = l.lowEnd(), hi = l.highEnd();
		m.box(0, 0, 1, 16, 7, 15, "sheet");
		m.box(lo ? 3.5 : 0, 7, 2, hi ? 12.5 : 16, 8.5, 12, "sheet");
		m.box(0, 7, 12, 16, 15, 15.5, "sheet");
		if (lo) {
			m.box(0, 7, 1, 3.5, 11.5, 15, "sheet");
			m.box(-0.5, 0, 3, 0, 8, 10, "sheet");
		}
		if (hi) {
			m.box(12.5, 7, 1, 16, 11.5, 15, "sheet");
			m.box(16, 0, 5, 16.5, 8, 12, "sheet");
		}
		m.box(4, 0, 0.5, 12, 5, 1, "sheet");
		return m.done();
	}

	// --- tables ------------------------------------------------------------------------------------

	private static void tables(List<Piece> all) {
		piece(all, "round_table", "Round Table", Form.SINGLE, "tables").noFacing().model(l -> roundTable()).skin("wood", "f_mahogany");
		piece(all, "overturned_table", "Overturned Table", Form.SINGLE, "tables").soft()
				.model(l -> HotelFurniture.tipped(roundTable(), 16)).skin("wood", "f_mahogany");
		piece(all, "dining_table", "Laid Dining Table", Form.TABLE, "tables").noFacing().model(FurnitureCatalog::clothTable)
				.skin("cloth", "f_tablecloth", "wood", "f_mahogany");
		piece(all, "wooden_table", "Mahogany Table", Form.TABLE, "tables").noFacing().model(FurnitureCatalog::woodTable)
				.skin("wood", "f_mahogany");
		piece(all, "pine_table", "Pine Table", Form.TABLE, "tables").noFacing().model(FurnitureCatalog::woodTable).skin("wood", "f_pine");
		piece(all, "prep_table", "Kitchen Table", Form.TABLE, "tables").noFacing().feel(Feel.METAL).model(FurnitureCatalog::prepTable)
				.skin("top", "f_zinc", "iron", "f_iron");
		piece(all, "billiard_table", "Billiard Table", Form.TABLE, "tables").noFacing().model(FurnitureCatalog::billiardTable)
				.skin("wood", "f_mahogany", "baize", "f_baize");
		piece(all, "torn_billiard_table", "Ruined Billiard Table", Form.TABLE, "tables").noFacing().model(FurnitureCatalog::billiardTable)
				.skin("wood", "f_mahogany_worn", "baize", "f_baize_torn");
		piece(all, "writing_desk", "Writing Desk", Form.SINGLE, "tables").model(l -> desk())
				.skin("wood", "f_mahogany", "drawers", "f_drawers", "leather", "f_desk_top");
		piece(all, "nightstand", "Nightstand", Form.SINGLE, "tables").model(l -> nightstand())
				.skin("wood", "f_mahogany", "drawers", "f_drawers");
		piece(all, "dresser", "Chest of Drawers", Form.SINGLE, "tables").model(l -> dresser())
				.skin("wood", "f_mahogany", "drawers", "f_drawers", "top", "marble_cream");
		piece(all, "console_table", "Console Table", Form.SINGLE, "tables").model(l -> consoleTable())
				.skin("wood", "f_mahogany", "top", "marble_black");
	}

	private static List<Box> roundTable() {
		M m = new M();
		m.box(3, 0, 7, 13, 1.5, 9, "wood");
		m.box(7, 0, 3, 9, 1.5, 13, "wood");
		m.box(6.5, 1.5, 6.5, 9.5, 4, 9.5, "wood");
		m.box(7, 4, 7, 9, 13, 9, "wood");
		m.box(6, 12, 6, 10, 14, 10, "wood");
		m.box(1, 14, 3, 15, 16, 13, "wood");
		m.box(3, 14, 1, 13, 16, 15, "wood");
		m.box(2, 14, 2, 14, 16, 14, "wood");
		return m.done();
	}

	/** A table under a white cloth that hangs down its sides where it doesn't join another. */
	private static List<Box> clothTable(Look l) {
		M m = new M();
		boolean n = !l.joined(0), e = !l.joined(1), s = !l.joined(2), w = !l.joined(3);
		m.box(0, 15, 0, 16, 16, 16, "cloth");
		m.box(w ? 0.6 : 0, 14, n ? 0.6 : 0, e ? 15.4 : 16, 15, s ? 15.4 : 16, "wood").only(DOWN);
		double z0 = n ? 0.6 : 0, z1 = s ? 15.4 : 16;
		if (n) {
			m.box(0, 5, 0, 16, 15, 0.6, "cloth").top(null);
		}
		if (s) {
			m.box(0, 5, 15.4, 16, 15, 16, "cloth").top(null);
		}
		if (w) {
			m.box(0, 5, z0, 0.6, 15, z1, "cloth").top(null);
		}
		if (e) {
			m.box(15.4, 5, z0, 16, 15, z1, "cloth").top(null);
		}
		for (int corner = 0; corner < 4; corner++) {
			boolean east = corner % 2 == 1, south = corner >= 2;
			if ((east ? e : w) && (south ? s : n)) {
				double x = east ? 13 : 1.5, z = south ? 13 : 1.5;
				m.box(x, 0, z, x + 1.5, 5, z + 1.5, "wood");
			}
		}
		return m.done();
	}

	/** A plain table: a top, an apron along its outer edges, turned legs at its outer corners. */
	private static List<Box> woodTable(Look l) {
		M m = new M();
		boolean n = !l.joined(0), e = !l.joined(1), s = !l.joined(2), w = !l.joined(3);
		m.box(0, 14, 0, 16, 16, 16, "wood");
		if (n) {
			m.box(0, 11, 1, 16, 14, 2, "wood");
		}
		if (s) {
			m.box(0, 11, 14, 16, 14, 15, "wood");
		}
		if (w) {
			m.box(1, 11, 0, 2, 14, 16, "wood");
		}
		if (e) {
			m.box(14, 11, 0, 15, 14, 16, "wood");
		}
		for (int corner = 0; corner < 4; corner++) {
			boolean east = corner % 2 == 1, south = corner >= 2;
			if ((east ? e : w) && (south ? s : n)) {
				double x = east ? 12.5 : 1, z = south ? 12.5 : 1;
				m.box(x + 0.5, 0, z + 0.5, x + 2, 11, z + 2, "wood");
				m.box(x, 7, z, x + 2.5, 8.5, z + 2.5, "wood");
			}
		}
		return m.done();
	}

	/** The kitchen's tables: a zinc top on iron legs, a shelf below for the pans. */
	private static List<Box> prepTable(Look l) {
		M m = new M();
		boolean n = !l.joined(0), e = !l.joined(1), s = !l.joined(2), w = !l.joined(3);
		m.box(0, 14, 0, 16, 16, 16, "top");
		m.box(0, 3, 0, 16, 4, 16, "top");
		for (int corner = 0; corner < 4; corner++) {
			boolean east = corner % 2 == 1, south = corner >= 2;
			if ((east ? e : w) && (south ? s : n)) {
				double x = east ? 14 : 0.75, z = south ? 14 : 0.75;
				m.box(x, 0, z, x + 1.25, 14, z + 1.25, "iron");
			}
		}
		return m.done();
	}

	/** A billiard table: green baize inside cushioned rails, pockets at the corners, heavy legs. */
	private static List<Box> billiardTable(Look l) {
		M m = new M();
		boolean n = !l.joined(0), e = !l.joined(1), s = !l.joined(2), w = !l.joined(3);
		m.box(0, 9, 0, 16, 13, 16, "wood");
		m.box(0, 13, 0, 16, 13.5, 16, "baize");
		if (n) {
			m.box(0, 13, 0, 16, 15, 2, "wood").face(SOUTH, "baize");
		}
		if (s) {
			m.box(0, 13, 14, 16, 15, 16, "wood").face(NORTH, "baize");
		}
		if (w) {
			m.box(0, 13, n ? 2 : 0, 2, 15, s ? 14 : 16, "wood").face(EAST, "baize");
		}
		if (e) {
			m.box(14, 13, n ? 2 : 0, 16, 15, s ? 14 : 16, "wood").face(WEST, "baize");
		}
		for (int corner = 0; corner < 4; corner++) {
			boolean east = corner % 2 == 1, south = corner >= 2;
			if ((east ? e : w) && (south ? s : n)) {
				double x = east ? 12 : 1, z = south ? 12 : 1;
				m.box(x, 0, z, x + 3, 9, z + 3, "wood");
				m.box(x - 0.5, 0, z - 0.5, x + 3.5, 1.5, z + 3.5, "wood");
				double px = east ? 13 : 1, pz = south ? 13 : 1;
				m.box(px, 13.5, pz, px + 2, 15.1, pz + 2, "f_pocket");
			}
		}
		return m.done();
	}

	/** A pedestal desk: drawers either side of the kneehole, a leather top. */
	private static List<Box> desk() {
		M m = new M();
		m.box(0.5, 0, 1.5, 5.5, 14, 15, "wood").front("drawers").fit();
		m.box(10.5, 0, 1.5, 15.5, 14, 15, "wood").front("drawers").fit();
		m.box(5.5, 4, 13, 10.5, 14, 15, "wood");
		m.box(5.5, 12, 1.5, 10.5, 14, 2.5, "wood");
		m.box(0, 14, 0.5, 16, 16, 16, "wood").top("leather").fit();
		return m.done();
	}

	private static List<Box> nightstand() {
		M m = new M();
		legs(m, 2.5, 3.5, 13.5, 15, 1.5, 3, "wood");
		m.box(2, 3, 3, 14, 13, 15, "wood").front("drawers").fit();
		m.box(1.5, 13, 2.5, 14.5, 14.5, 15.5, "wood");
		return m.done();
	}

	private static List<Box> dresser() {
		M m = new M();
		legs(m, 1.5, 3, 14.5, 15.5, 2, 2, "wood");
		m.box(1, 2, 2.5, 15, 15, 15.5, "wood").front("drawers").fit();
		m.box(0.5, 15, 2, 15.5, 16, 16, "top");
		return m.done();
	}

	/** A narrow table against a corridor wall, a black marble top on gilt-edged legs. */
	private static List<Box> consoleTable() {
		M m = new M();
		m.box(0.5, 14, 8, 15.5, 15.5, 16, "top");
		m.box(1, 11, 8.5, 15, 14, 9.5, "wood");
		m.box(1.5, 0, 9, 3, 14, 10.5, "wood");
		m.box(13, 0, 9, 14.5, 14, 10.5, "wood");
		m.box(1.5, 0, 14, 3, 14, 15.5, "wood");
		m.box(13, 0, 14, 14.5, 14, 15.5, "wood");
		m.box(3, 2, 9.5, 13, 3, 15, "wood");
		return m.done();
	}

	// --- counters ------------------------------------------------------------------------------------

	private static void counters(List<Piece> all) {
		piece(all, "bar_counter", "Bar Counter", Form.RUN, "counters").model(FurnitureCatalog::barCounter)
				.skin("wood", "f_mahogany", "panel", "f_bar_panel");
		piece(all, "reception_desk", "Reception Desk", Form.RUN, "counters").model(FurnitureCatalog::receptionDesk)
				.skin("wood", "f_mahogany", "panel", "f_desk_diamond");
		piece(all, "kitchen_range", "Kitchen Range", Form.SINGLE, "counters").feel(Feel.METAL).model(l -> range())
				.skin("iron", "f_iron", "front", "f_range_front", "hob", "f_range_top");
		piece(all, "kitchen_sink", "Scullery Sink", Form.SINGLE, "counters").feel(Feel.STONE).model(l -> sink())
				.skin("brick", "minecraft:block/bricks", "basin", "f_enamel_grimy");
		piece(all, "ice_box", "Ice Box", Form.MULTI, "counters").size(1, 2, 1).model(l -> iceBox())
				.skin("wood", "f_oak", "door", "f_icebox_door");
	}

	/** The bar, a block of it: panelled front with a brass foot rail, a shelf behind for the barman. */
	private static List<Box> barCounter(Look l) {
		M m = new M();
		m.box(0, 1.5, 2.5, 16, 14.5, 14, "wood").front("panel").fit();
		m.box(0, 0, 2, 16, 1.5, 14.5, "f_mahogany_dark");
		m.box(0, 14.5, 0.5, 16, 16, 16, "wood");
		m.box(0, 7, 14, 16, 8, 16, "wood");
		m.box(0, 0, 14.5, 16, 1, 16, "wood");
		m.box(0, 2.5, 0.5, 16, 3.5, 1.5, "brass");
		m.box(7.5, 1.5, 1, 8.5, 3.5, 2.5, "brass");
		if (l.lowEnd()) {
			m.box(0, 0, 2.5, 1, 14.5, 16, "wood");
		}
		if (l.highEnd()) {
			m.box(15, 0, 2.5, 16, 14.5, 16, "wood");
		}
		return m.done();
	}

	/** The front desk: carved diamond panels, a brass edge to its top, pigeonholes behind it. */
	private static List<Box> receptionDesk(Look l) {
		M m = new M();
		m.box(0, 1, 2, 16, 14, 15, "wood").front("panel").fit();
		m.box(0, 0, 1.5, 16, 1, 15.5, "f_mahogany_dark");
		m.box(0, 14, 0.5, 16, 15.5, 16, "wood");
		m.box(0, 13.25, 0.25, 16, 14, 1, "brass");
		m.box(0, 15.5, 1, 16, 16, 15.5, "f_desk_top");
		m.box(0, 9, 15, 16, 10, 16, "wood");
		if (l.lowEnd()) {
			m.box(0, 0, 1.5, 1.5, 14, 16, "wood");
		}
		if (l.highEnd()) {
			m.box(14.5, 0, 1.5, 16, 14, 16, "wood");
		}
		return m.done();
	}

	/** A black iron range: two ovens, a hob, a brass rail, its flue going up behind. */
	private static List<Box> range() {
		M m = new M();
		m.box(0, 1, 1, 16, 13, 16, "iron").front("front").fit();
		m.box(0.5, 0, 1.5, 2, 1, 3, "iron");
		m.box(14, 0, 1.5, 15.5, 1, 3, "iron");
		m.box(0.5, 0, 14, 2, 1, 15.5, "iron");
		m.box(14, 0, 14, 15.5, 1, 15.5, "iron");
		m.box(-0.25, 13, 0.5, 16.25, 14.5, 16, "iron").top("hob").fit();
		m.box(0.5, 9.5, -0.25, 15.5, 10.25, 0.5, "brass");
		m.box(0, 14.5, 13.5, 16, 22, 16, "iron");
		m.box(5.5, 22, 12.5, 10.5, 32, 16, "iron");
		return m.done();
	}

	/** A deep scullery sink on brick piers, its brass taps on the wall behind. */
	private static List<Box> sink() {
		M m = new M();
		m.box(0.5, 0, 4, 3, 10, 14, "brick");
		m.box(13, 0, 4, 15.5, 10, 14, "brick");
		m.box(0, 10, 1, 16, 11, 15, "basin");
		m.box(0, 11, 1, 16, 15, 2.5, "basin");
		m.box(0, 11, 13.5, 16, 15, 15, "basin");
		m.box(0, 11, 2.5, 1.5, 15, 13.5, "basin");
		m.box(14.5, 11, 2.5, 16, 15, 13.5, "basin");
		for (double x : new double[] {4.5, 10.5}) {
			m.box(x, 15, 14, x + 1.5, 19, 15.5, "brass");
			m.box(x + 0.25, 17.5, 10.5, x + 1.25, 18.5, 14, "brass");
			m.box(x + 0.25, 16, 10.5, x + 1.25, 17.5, 11.5, "brass");
			m.box(x - 0.5, 19, 14, x + 2, 19.75, 15.5, "brass");
		}
		return m.done();
	}

	private static List<Box> iceBox() {
		M m = new M();
		m.box(1, 0, 2, 15, 1.5, 15.5, "wood");
		m.box(1.5, 1.5, 2.5, 14.5, 26, 15, "wood");
		m.box(2, 2, 2, 14, 13, 2.5, "wood").front("door").fit();
		m.box(2, 14, 2, 14, 25.5, 2.5, "wood").front("door").fit();
		m.box(1, 26, 2, 15, 27.5, 15.5, "wood");
		for (double y : new double[] {9, 21}) {
			m.box(11.5, y, 1.25, 13.5, y + 1.5, 2, "brass");
		}
		return m.done();
	}

	// --- storage -------------------------------------------------------------------------------------

	private static void storage(List<Piece> all) {
		piece(all, "wardrobe", "Wardrobe", Form.MULTI, "storage").size(2, 2, 1).model(l -> wardrobe())
				.skin("wood", "f_mahogany", "door", "f_wardrobe_door");
		piece(all, "filing_cabinet", "Filing Cabinet", Form.SINGLE, "storage").model(l -> filingCabinet())
				.skin("wood", "f_oak", "front", "f_filing_front");
		piece(all, "staff_locker", "Staff Locker", Form.MULTI, "storage").size(1, 2, 1).feel(Feel.METAL).model(l -> locker())
				.skin("steel", "f_locker_steel", "front", "f_locker_front");
		piece(all, "steamer_trunk", "Steamer Trunk", Form.SINGLE, "storage").model(l -> trunk())
				.skin("canvas", "f_trunk", "strap", "f_leather_brown");
		piece(all, "suitcases", "Suitcases", Form.SINGLE, "storage").feel(Feel.CLOTH).model(l -> suitcases())
				.skin("case", "f_leather_brown", "tan", "f_leather_tan", "box", "f_hatbox");
	}

	/** A double wardrobe: tall panelled doors, a moulded cornice; something to hide in, later. */
	private static List<Box> wardrobe() {
		M m = new M();
		m.box(0, 0, 1, 32, 1.5, 16, "f_mahogany_dark");
		m.box(0.5, 1.5, 1.5, 31.5, 29, 16, "wood");
		m.box(1, 1.5, 1, 16, 28.5, 1.5, "wood").front("door").fit();
		m.box(16, 1.5, 1, 31, 28.5, 1.5, "wood").front("door").fit();
		m.box(14.25, 13, 0.5, 15, 17, 1, "brass");
		m.box(17, 13, 0.5, 17.75, 17, 1, "brass");
		m.box(0, 29, 0.5, 32, 30.5, 16, "wood");
		m.box(-0.5, 30.5, 0, 32.5, 31.75, 16, "wood");
		return m.done();
	}

	private static List<Box> filingCabinet() {
		M m = new M();
		m.box(2, 0, 2, 14, 15, 15, "wood").front("front").fit();
		m.box(1.5, 15, 1.5, 14.5, 16, 15.5, "wood");
		return m.done();
	}

	private static List<Box> locker() {
		M m = new M();
		m.box(1, 0, 2, 15, 30, 16, "steel").front("front").fit();
		m.box(0.75, 30, 1.75, 15.25, 30.75, 16, "steel");
		return m.done();
	}

	/** A steamer trunk: striped canvas over wood, brass corners, leather straps. */
	private static List<Box> trunk() {
		M m = new M();
		m.box(1, 0, 3, 15, 9, 14, "canvas");
		m.box(1.25, 9, 3.5, 14.75, 10.5, 13.5, "canvas");
		for (double x : new double[] {0.75, 13.75}) {
			for (double z : new double[] {2.75, 12.75}) {
				m.box(x, 0, z, x + 1.5, 10.6, z + 1.5, "brass");
			}
		}
		m.box(4, 0, 2.75, 5.5, 10.75, 14.25, "strap");
		m.box(10.5, 0, 2.75, 12, 10.75, 14.25, "strap");
		m.box(7, 6, 2.6, 9, 8.5, 3, "brass");
		return m.done();
	}

	private static List<Box> suitcases() {
		M m = new M();
		m.box(1, 0, 3, 15, 4, 13, "case");
		m.box(4.5, 4, 7.5, 6, 4.75, 8.5, "brass");
		m.box(10, 4, 7.5, 11.5, 4.75, 8.5, "brass");
		m.box(2.5, 4, 4, 13.5, 7.5, 12, "tan").turn('y', 22.5);
		round(m, 8, 8, 7.5, 11.5, 3.5, "box");
		return m.done();
	}

	// --- beds ----------------------------------------------------------------------------------------

	private static void beds(List<Piece> all) {
		piece(all, "hotel_bed", "Hotel Bed", Form.MULTI, "beds").size(2, 1, 2).feel(Feel.CLOTH).model(l -> hotelBed(true, false))
				.skin("wood", "f_mahogany", "panel", "f_mahogany_panel", "sheet", "f_linen", "blanket", "f_blanket_red", "pillow",
						"f_pillow");
		piece(all, "stripped_bed", "Stripped Bed", Form.MULTI, "beds").size(2, 1, 2).feel(Feel.CLOTH).model(l -> hotelBed(false, false))
				.skin("wood", "f_mahogany_worn", "panel", "f_mahogany_panel", "sheet", "f_mattress", "pillow", "f_linen_dirty");
		piece(all, "burned_bed", "Burned Bed", Form.MULTI, "beds").size(2, 1, 2).model(l -> hotelBed(false, true))
				.skin("wood", "f_charred_wood", "panel", "f_charred_wood", "sheet", "f_ash", "pillow", "f_ash");
		piece(all, "four_poster_bed", "Four-Poster Bed", Form.MULTI, "beds").size(2, 3, 2).feel(Feel.CLOTH).model(l -> fourPoster())
				.skin("wood", "f_mahogany", "panel", "f_mahogany_panel", "sheet", "f_linen", "blanket", "f_velvet_red", "pillow", "f_pillow",
						"velvet", "f_curtain_red", "fringe", "f_gold_fringe");
		piece(all, "iron_bed", "Iron Bed", Form.MULTI, "beds").size(1, 1, 2).feel(Feel.METAL).model(l -> ironBed(0))
				.skin("iron", "f_iron", "sheet", "f_mattress", "blanket", "f_blanket_grey", "pillow", "f_linen_dirty");
		piece(all, "iron_bunk", "Iron Bunk", Form.MULTI, "beds").size(1, 2, 2).feel(Feel.METAL).model(l -> bunk())
				.skin("iron", "f_iron", "sheet", "f_mattress", "blanket", "f_blanket_grey", "pillow", "f_linen_dirty");
		piece(all, "crib", "Crib", Form.SINGLE, "beds").model(l -> crib())
				.skin("paint", "f_white_paint", "sheet", "f_linen", "blanket", "f_blanket_pink");
	}

	/**
	 * A double bed: panelled head and foot, made up with the red blanket over its foot; or
	 * stripped to its stained mattress; or burned, the bedding gone to ash.
	 */
	private static List<Box> hotelBed(boolean made, boolean burned) {
		M m = new M();
		legs(m, 1, 1, 31, 31, 3, 3, "wood");
		m.box(0.5, 3, 1.5, 2, 7, 30.5, "wood");
		m.box(30, 3, 1.5, 31.5, 7, 30.5, "wood");
		m.box(2, 5, 2, 30, burned ? 7 : 10, 30, "sheet");
		if (made) {
			m.box(1.5, 5.5, 1.75, 30.5, 10.6, 21, "blanket");
			m.box(2, 10, 19, 30, 10.9, 22.5, "sheet").face(DOWN, null);
			m.box(4, 10, 23.5, 15, 13, 29.5, "pillow");
			m.box(17, 10, 23.5, 28, 13, 29.5, "pillow");
		} else if (!burned) {
			m.box(5, 10, 23.5, 15, 12.5, 29, "pillow").turn('y', 22.5);
		}
		m.box(0, 0, 30, 32, 10, 32, "wood");
		if (burned) {
			m.box(1, 10, 30, 13, 17, 32, "wood");
			m.box(13, 10, 30, 22, 13, 32, "wood");
			m.box(-0.5, 0, 29, 1.5, 19, 32, "wood");
			m.box(30.5, 0, 29, 32.5, 12, 32, "wood");
			m.box(1, 0, 0, 31, 7, 1.5, "wood");
			m.box(-0.5, 0, -0.5, 1.5, 9, 1.5, "wood");
			return m.done();
		}
		m.box(1, 10, 30, 16, 21, 32, "wood").front("panel").fit();
		m.box(16, 10, 30, 31, 21, 32, "wood").front("panel").fit();
		m.box(-0.5, 0, 29, 1.5, 23, 32, "wood");
		m.box(30.5, 0, 29, 32.5, 23, 32, "wood");
		m.box(0, 21, 29.5, 32, 22.5, 32, "wood");
		m.box(1, 0, 0, 16, 9, 1.5, "wood").front("panel").fit();
		m.box(16, 0, 0, 31, 9, 1.5, "wood").front("panel").fit();
		m.box(-0.5, 0, -0.5, 1.5, 11, 1.5, "wood");
		m.box(30.5, 0, -0.5, 32.5, 11, 1.5, "wood");
		m.box(0, 9, -0.25, 32, 10, 1.75, "wood");
		return m.done();
	}

	/** Wennberg's bed: turned posts to a velvet canopy, curtains gathered at the posts. */
	private static List<Box> fourPoster() {
		M m = new M();
		m.add(hotelBed(true, false), 0, 0, 0);
		for (double x : new double[] {-0.5, 30}) {
			for (double z : new double[] {-0.5, 29.5}) {
				m.box(x, 0, z, x + 2.5, 44, z + 2.5, "wood");
				m.box(x - 0.25, 18, z - 0.25, x + 2.75, 20, z + 2.75, "wood");
				m.box(x + (x < 0 ? 2.5 : -2.5), 4, z + 0.5, x + (x < 0 ? 5 : 0), 42, z + 2, "velvet");
			}
		}
		m.box(-1, 44, -1, 33, 47, 0.5, "velvet").front("velvet");
		m.box(-1, 43, -1, 33, 44, 0.5, "fringe").only(NORTH, DOWN);
		m.box(-1, 44, 31.5, 33, 47, 32, "velvet");
		m.box(-1, 44, 0.5, 0.5, 47, 31.5, "velvet");
		m.box(31.5, 44, 0.5, 33, 47, 31.5, "velvet");
		m.box(-1, 46.5, -1, 33, 47.5, 32, "wood");
		m.box(0.5, 45, 0.5, 31.5, 46.5, 31.5, "velvet").only(DOWN);
		return m.done();
	}

	/** A servant's iron bed: black tubes, brass knobs, a thin grey blanket. */
	private static List<Box> ironBed(double y) {
		M m = new M();
		for (double x : new double[] {0.5, 14}) {
			m.box(x, y, 30, x + 1.5, y + 20, 31.5, "iron");
			m.box(x - 0.25, y + 20, 29.75, x + 1.75, y + 21.5, 31.75, "brass");
			m.box(x, y, 0.5, x + 1.5, y + 13, 2, "iron");
			m.box(x - 0.25, y + 13, 0.25, x + 1.75, y + 14.5, 2.25, "brass");
			m.box(x + 0.25, y + 5, 2, x + 1.25, y + 6, 30, "iron");
		}
		for (double h : new double[] {8, 18}) {
			m.box(2, y + h, 30.25, 14, y + h + 1, 31.25, "iron");
		}
		m.box(2, y + 11, 0.75, 14, y + 12, 1.75, "iron");
		for (double x = 3; x < 14; x += 3) {
			m.box(x, y + 8, 30.5, x + 0.75, y + 18, 31, "iron");
			m.box(x, y + 5, 1, x + 0.75, y + 11, 1.5, "iron");
		}
		m.box(1.5, y + 5, 1.5, 14.5, y + 8.5, 30, "sheet");
		m.box(1.25, y + 5.5, 1.25, 14.75, y + 9, 21, "blanket");
		m.box(3, y + 8.5, 24, 13, y + 10.5, 29.5, "pillow");
		return m.done();
	}

	private static List<Box> bunk() {
		M m = new M();
		m.add(ironBed(0), 0, 0, 0);
		m.add(ironBed(15), 0, 0, 0);
		return m.done();
	}

	private static List<Box> crib() {
		M m = new M();
		for (double x : new double[] {1, 13.5}) {
			for (double z : new double[] {2, 12.5}) {
				m.box(x, 0, z, x + 1.5, 14, z + 1.5, "paint");
			}
		}
		m.box(2.5, 12.5, 2, 13.5, 13.5, 3, "paint");
		m.box(2.5, 12.5, 13, 13.5, 13.5, 14, "paint");
		m.box(1.25, 12.5, 3.5, 2.25, 13.5, 12.5, "paint");
		m.box(13.75, 12.5, 3.5, 14.75, 13.5, 12.5, "paint");
		for (double x = 3.5; x < 13; x += 2) {
			m.box(x, 4, 2.25, x + 0.6, 12.5, 2.75, "paint");
			m.box(x, 4, 13.25, x + 0.6, 12.5, 13.75, "paint");
		}
		for (double z = 4.5; z < 12; z += 2) {
			m.box(1.5, 4, z, 2, 12.5, z + 0.6, "paint");
			m.box(14, 4, z, 14.5, 12.5, z + 0.6, "paint");
		}
		m.box(2.5, 3, 3, 13.5, 5.5, 13, "sheet");
		m.box(2.75, 5.5, 3.25, 13.25, 6.5, 9.5, "blanket");
		return m.done();
	}

	// --- lights --------------------------------------------------------------------------------------

	private static void lights(List<Piece> all) {
		piece(all, "wall_sconce", "Wall Sconce", Form.SINGLE, "lights").mount(Mount.WALL).feel(Feel.METAL).light(Light.ELECTRIC, 6)
				.model(l -> sconce()).skin("shade", "f_glass_tulip");
		piece(all, "chandelier", "Chandelier", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.GLASS).noFacing()
				.light(Light.ELECTRIC, 12).model(l -> chandelier()).skin("bulb", "f_bulb", "crystal", "f_crystal");
		piece(all, "grand_chandelier", "Grand Chandelier", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.GLASS).noFacing()
				.light(Light.ELECTRIC, 15).model(l -> grandChandelier()).skin("bulb", "f_bulb", "crystal", "f_crystal");
		piece(all, "chandelier_chain", "Chandelier Chain", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.METAL).noFacing()
				.model(l -> chain());
		piece(all, "pendant_lantern", "Pendant Lantern", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.METAL).noFacing()
				.light(Light.ELECTRIC, 8).model(l -> lantern()).skin("glass", "f_lantern_glass");
		piece(all, "enamel_pendant", "Enamel Pendant Lamp", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.METAL).noFacing()
				.light(Light.ELECTRIC, 8).model(l -> enamelPendant()).skin("enamel", "f_enamel_green", "bulb", "f_bulb");
		piece(all, "caged_bulb", "Caged Bulb", Form.SINGLE, "lights").mount(Mount.CEILING).feel(Feel.METAL).noFacing()
				.light(Light.ELECTRIC, 6).model(l -> cagedBulb()).skin("iron", "f_iron", "bulb", "f_bulb");
		piece(all, "bankers_lamp", "Banker's Lamp", Form.SINGLE, "lights").soft().feel(Feel.METAL).light(Light.ELECTRIC, 8)
				.model(l -> bankersLamp()).skin("shade", "f_shade_green");
		piece(all, "silk_lamp", "Silk-Shaded Lamp", Form.SINGLE, "lights").soft().noFacing().feel(Feel.CLOTH).light(Light.ELECTRIC, 8)
				.model(l -> silkLamp()).skin("shade", "f_shade_silk", "body", "f_porcelain");
		piece(all, "floor_lamp", "Standard Lamp", Form.MULTI, "lights").size(1, 2, 1).noFacing().feel(Feel.METAL).light(Light.ELECTRIC, 9)
				.glowParts(1).model(l -> floorLamp()).skin("shade", "f_shade_silk", "fringe", "f_gold_fringe");
		piece(all, "candlestick", "Candlestick", Form.SINGLE, "lights").soft().noFacing().feel(Feel.METAL).light(Light.FLAME, 4)
				.flame(8, 12.8, 8).model(l -> candlestick()).skin("wax", "f_wax", "wick", "f_wick");
		Piece candelabra = piece(all, "candelabra", "Candelabra", Form.SINGLE, "lights").soft().noFacing().feel(Feel.METAL)
				.light(Light.FLAME, 7).model(l -> candelabra()).skin("wax", "f_wax", "wick", "f_wick", "silver", "f_silver");
		candelabra.flame(8, 15.3, 8).flame(3, 13.3, 8).flame(13, 13.3, 8).flame(8, 13.3, 3).flame(8, 13.3, 13);
		piece(all, "candles", "Candles", Form.SINGLE, "lights").feel(Feel.WOOD).noFacing().soft().light(Light.FLAME, 5)
				.model(l -> candles()).skin("wax", "f_wax", "wick", "f_wick").flame(6, 9.3, 6).flame(10, 6.3, 7).flame(7.5, 12.3, 10);
		piece(all, "bottle_candle", "Candle in a Bottle", Form.SINGLE, "lights").feel(Feel.GLASS).noFacing().soft().light(Light.FLAME, 4)
				.flame(8, 14.8, 8).model(l -> bottleCandle()).skin("wax", "f_wax", "wick", "f_wick", "bottle", "f_bottle_green");
		piece(all, "oil_lamp", "Oil Lamp", Form.SINGLE, "lights").feel(Feel.GLASS).noFacing().soft().light(Light.FLAME, 6)
				.model(l -> oilLamp()).skin("glass", "f_chimney");
		piece(all, "fireplace", "Marble Fireplace", Form.MULTI, "lights").size(3, 2, 1).feel(Feel.STONE).light(Light.FLAME, 10)
				.glowParts(1).fire(24, 7, 11).model(FurnitureCatalog::fireplace)
				.skin("marble", "marble_cream", "hearth", "marble_black", "soot", "f_soot", "iron", "f_iron", "log", "f_log", "fire", "f_fire",
						"ash", "f_ash");
	}

	/** A brass sconce on the wall, its arm curling up to a frosted tulip shade. */
	private static List<Box> sconce() {
		M m = new M();
		m.box(6.5, 5, 15, 9.5, 11, 16, "brass");
		m.box(7.5, 7, 11, 8.5, 8, 15, "brass");
		m.box(7.5, 7, 10, 8.5, 10, 11, "brass");
		m.box(6.5, 10, 9, 9.5, 11, 12, "brass");
		m.box(6.75, 11, 8.75, 9.25, 12, 11.25, "shade").glow();
		m.box(6, 12, 8, 10, 15, 12, "shade").glow();
		return m.done();
	}

	/** A brass chandelier of eight arms with candle bulbs, hung with crystal drops. */
	private static List<Box> chandelier() {
		M m = new M();
		m.box(6, 15, 6, 10, 16, 10, "brass");
		m.box(7.5, 9, 7.5, 8.5, 15, 8.5, "brass");
		m.box(6.5, 5, 6.5, 9.5, 9, 9.5, "brass");
		m.box(7, 2, 7, 9, 5, 9, "crystal");
		for (double a : new double[] {0, 45, -45}) {
			m.box(1.5, 6, 7.5, 14.5, 7, 8.5, "brass");
			if (a != 0) {
				m.turn('y', a, 8, 6.5, 8);
			}
		}
		m.box(7.5, 6, 1.5, 8.5, 7, 14.5, "brass");
		for (double a : new double[] {0, 45, -45, 90}) {
			for (double x : new double[] {1.25, 13.25}) {
				if (a == 90) {
					m.box(7.25, 7, x, 8.75, 10, x + 1.5, "bulb").glow();
					m.box(7.5, 3.5, x + 0.25, 8.5, 6, x + 1.25, "crystal");
				} else {
					m.box(x, 7, 7.25, x + 1.5, 10, 8.75, "bulb").glow();
					if (a != 0) {
						m.turn('y', a, 8, 7, 8);
					}
					m.box(x + 0.25, 3.5, 7.5, x + 1.25, 6, 8.5, "crystal");
					if (a != 0) {
						m.turn('y', a, 8, 3.5, 8);
					}
				}
			}
		}
		return m.done();
	}

	/**
	 * The grand chandeliers of the ballroom and the atrium, three blocks across and two deep under
	 * the block they hang from: a crown of crystal, a ring of sixteen candle bulbs, swags of crystal
	 * hanging from it, and a crystal bowl tapering to a drop.
	 */
	private static List<Box> grandChandelier() {
		M m = new M();
		m.box(5, 15, 5, 11, 16, 11, "brass");
		m.box(7.5, 1, 7.5, 8.5, 15, 8.5, "brass");
		ring(m, 8, 4, 8, 6, 0.4, 6, 0.5, "crystal", false);
		ring(m, 8, 3, 8, 3.5, 0.4, 7, 0.5, "crystal", false);
		round(m, 8, 8, 2, 4, 6, "brass");
		m.box(7, -6, 7, 9, 1, 9, "brass");
		ring(m, 8, -5, 8, 14, 1.2, 1.2, 5.6, "brass", false);
		for (double a : new double[] {0, 45, -45}) {
			m.box(-6, -5.5, 7.5, 22, -4.5, 8.5, "brass");
			if (a != 0) {
				m.turn('y', a, 8, -5, 8);
			}
		}
		m.box(7.5, -5.5, -6, 8.5, -4.5, 22, "brass");
		ring(m, 8, -3.8, 8, 13.6, 1.2, 3, 1.2, "bulb", true);
		ring(m, 8, -11, 8, 13.4, 0.4, 6, 0.4, "crystal", false);
		ring(m, 8, -12, 8, 9.5, 0.4, 6.5, 0.4, "crystal", false);
		ring(m, 8, -14, 8, 5, 0.4, 5, 0.4, "crystal", false);
		round(m, 8, 8, -9, -6, 3, "brass");
		m.box(7.5, -15, 7.5, 8.5, -9, 8.5, "crystal");
		m.box(7, -16, 7, 9, -15, 9, "crystal");
		return m.done();
	}

	private static List<Box> chain() {
		M m = new M();
		for (int i = 0; i < 4; i++) {
			if (i % 2 == 0) {
				m.box(7.5, i * 4, 6.5, 8.5, i * 4 + 4, 9.5, "brass");
			} else {
				m.box(6.5, i * 4, 7.5, 9.5, i * 4 + 4, 8.5, "brass");
			}
		}
		return m.done();
	}

	/** A corridor lantern on a chain: amber glass in a brass frame, a finial below. */
	private static List<Box> lantern() {
		M m = new M();
		m.box(7.5, 11, 7.5, 8.5, 16, 8.5, "brass");
		m.box(5, 9, 5, 11, 11, 11, "brass");
		m.box(4.5, 8.5, 4.5, 11.5, 9.25, 11.5, "brass");
		m.box(5.25, 2.5, 5.25, 10.75, 8.5, 10.75, "glass").glow();
		m.box(5, 2, 5, 11, 2.75, 11, "brass");
		m.box(6.5, 0, 6.5, 9.5, 2, 9.5, "brass");
		return m.done();
	}

	private static List<Box> enamelPendant() {
		M m = new M();
		m.box(7.75, 7, 7.75, 8.25, 16, 8.25, "f_iron");
		m.box(6.5, 6, 6.5, 9.5, 7, 9.5, "enamel");
		m.box(4.5, 4, 4.5, 11.5, 6, 11.5, "enamel");
		m.box(3, 3, 3, 13, 4, 13, "enamel");
		m.box(6.5, 2, 6.5, 9.5, 3, 9.5, "bulb").glow();
		return m.done();
	}

	private static List<Box> cagedBulb() {
		M m = new M();
		m.box(5.5, 15, 5.5, 10.5, 16, 10.5, "iron");
		m.box(6.75, 13, 6.75, 9.25, 15, 9.25, "brass");
		m.box(6.5, 9, 6.5, 9.5, 13, 9.5, "bulb").glow();
		for (double x : new double[] {5, 10.5}) {
			m.box(x, 8, 5, x + 0.5, 15, 5.5, "iron");
			m.box(x, 8, 10.5, x + 0.5, 15, 11, "iron");
		}
		m.box(5, 8, 5, 11, 8.5, 11, "iron").only(UP, DOWN);
		m.box(5, 11.5, 5, 11, 12, 5.5, "iron");
		m.box(5, 11.5, 10.5, 11, 12, 11, "iron");
		return m.done();
	}

	/** The green-shaded banker's lamp of the library and the offices. */
	private static List<Box> bankersLamp() {
		M m = new M();
		m.box(4.5, 0, 6, 11.5, 1, 10, "brass");
		m.box(7.5, 1, 9, 8.5, 6, 10, "brass");
		m.box(7.5, 5.5, 7, 8.5, 6.5, 10, "brass");
		m.box(3, 6, 5.5, 13, 8.5, 9.5, "shade").glow();
		m.box(3.5, 8.5, 6.5, 12.5, 9.25, 8.5, "shade").glow();
		return m.done();
	}

	private static List<Box> silkLamp() {
		M m = new M();
		round(m, 8, 8, 0, 1, 3, "brass");
		round(m, 8, 8, 1, 6, 2, "body");
		m.box(7.5, 6, 7.5, 8.5, 7, 8.5, "brass");
		round(m, 8, 8, 6, 7, 5, "shade", true);
		round(m, 8, 8, 7, 12, 4.5, "shade", true);
		return m.done();
	}

	private static List<Box> floorLamp() {
		M m = new M();
		round(m, 8, 8, 0, 1.5, 3.5, "brass");
		m.box(7.5, 1.5, 7.5, 8.5, 24, 8.5, "brass");
		m.box(7, 9, 7, 9, 10, 9, "brass");
		round(m, 8, 8, 23, 24, 5.5, "fringe");
		round(m, 8, 8, 24, 25, 5.5, "shade", true);
		round(m, 8, 8, 25, 31, 5, "shade", true);
		return m.done();
	}

	private static List<Box> candlestick() {
		M m = new M();
		round(m, 8, 8, 0, 1, 2.5, "brass");
		m.box(7, 1, 7, 9, 6, 9, "brass");
		round(m, 8, 8, 6, 7, 2, "brass");
		m.box(7.25, 7, 7.25, 8.75, 12, 8.75, "wax");
		m.box(7, 9.5, 7.75, 7.25, 11, 8.25, "wax");
		m.box(7.75, 12, 7.75, 8.25, 12.8, 8.25, "wick");
		return m.done();
	}

	private static List<Box> candelabra() {
		M m = new M();
		round(m, 8, 8, 0, 1, 3, "silver");
		m.box(7.25, 1, 7.25, 8.75, 10, 8.75, "silver");
		m.box(3, 8, 7.5, 13, 9, 8.5, "silver");
		m.box(7.5, 8, 3, 8.5, 9, 13, "silver");
		for (double[] c : new double[][] {{3, 8}, {13, 8}, {8, 3}, {8, 13}}) {
			m.box(c[0] - 1, 9, c[1] - 1, c[0] + 1, 10, c[1] + 1, "silver");
			m.box(c[0] - 0.6, 10, c[1] - 0.6, c[0] + 0.6, 13, c[1] + 0.6, "wax");
			m.box(c[0] - 0.25, 13, c[1] - 0.25, c[0] + 0.25, 13.6, c[1] + 0.25, "wick");
		}
		m.box(7, 10, 7, 9, 11, 9, "silver");
		m.box(7.4, 11, 7.4, 8.6, 15, 8.6, "wax");
		m.box(7.75, 15, 7.75, 8.25, 15.6, 8.25, "wick");
		return m.done();
	}

	/** Three church candles of different heights, burned down and dripping, in a pool of their wax. */
	private static List<Box> candles() {
		M m = new M();
		m.box(3.5, 0, 3.5, 12.5, 0.4, 12.5, "wax");
		m.box(5, 0, 5, 7, 8.5, 7, "wax");
		m.box(4.75, 6, 5.5, 5, 8, 6.25, "wax");
		m.box(5.75, 8.5, 5.75, 6.25, 9.3, 6.25, "wick");
		m.box(9, 0, 6, 11, 5.5, 8, "wax");
		m.box(11, 3, 6.5, 11.25, 5.25, 7.25, "wax");
		m.box(9.75, 5.5, 6.75, 10.25, 6.3, 7.25, "wick");
		m.box(6.5, 0, 9, 8.5, 11.5, 11, "wax");
		m.box(6.25, 9, 9.75, 6.5, 11.25, 10.5, "wax");
		m.box(7.25, 11.5, 9.75, 7.75, 12.3, 10.25, "wick");
		return m.done();
	}

	/** A candle stuck in a wine bottle, the wax run down over the glass: the chef's light. */
	private static List<Box> bottleCandle() {
		M m = new M();
		round(m, 8, 8, 0, 7, 2.5, "bottle");
		m.box(7, 7, 7, 9, 10, 9, "bottle");
		m.box(6.5, 9, 7.5, 9.5, 10.5, 8.5, "wax");
		m.box(9, 4, 7.5, 9.6, 9.5, 8.5, "wax");
		m.box(7.25, 10, 7.25, 8.75, 14, 8.75, "wax");
		m.box(7.75, 14, 7.75, 8.25, 14.8, 8.25, "wick");
		return m.done();
	}

	private static List<Box> oilLamp() {
		M m = new M();
		round(m, 8, 8, 0, 1, 3.5, "brass");
		round(m, 8, 8, 1, 5, 3, "brass");
		m.box(6.5, 5, 6.5, 9.5, 6, 9.5, "brass");
		round(m, 8, 8, 6, 9, 2.5, "glass", true);
		m.box(6.75, 9, 6.75, 9.25, 13, 9.25, "glass").glow();
		m.box(9.5, 2, 7.5, 11, 3, 8.5, "brass");
		return m.done();
	}

	/**
	 * A marble fireplace three blocks wide set against a wall: jambs, a frieze, the mantel shelf,
	 * a black hearth, an iron grate of logs. Lit, the fire burns (its glow from the middle block);
	 * out, it's cold ash.
	 */
	private static List<Box> fireplace(Look l) {
		M m = new M();
		m.box(2, 0, 0, 46, 1, 8, "hearth");
		m.box(0, 0, 8, 10, 25, 16, "marble");
		m.box(38, 0, 8, 48, 25, 16, "marble");
		m.box(10, 17, 8, 38, 25, 16, "marble");
		m.box(-1, 25, 6, 49, 28, 16, "marble");
		m.box(1, 2, 7.5, 9, 23, 8, "marble").only(NORTH);
		m.box(39, 2, 7.5, 47, 23, 8, "marble").only(NORTH);
		m.box(10, 1, 14, 38, 17, 16, "soot");
		m.box(10, 1, 8, 10.5, 17, 14, "soot").only(EAST);
		m.box(37.5, 1, 8, 38, 17, 14, "soot").only(WEST);
		m.box(10, 16.5, 8, 38, 17, 16, "soot").only(DOWN);
		m.box(15, 1, 9, 33, 2, 13, "iron");
		m.box(15, 2, 8.75, 33, 5, 9.5, "iron");
		m.box(15, 1, 9, 16, 6, 13, "iron");
		m.box(32, 1, 9, 33, 6, 13, "iron");
		if (l.lit()) {
			m.box(16, 2, 10, 32, 5, 12, "log").glow();
			m.box(17, 4, 11, 31, 15, 11, "fire").glow();
			m.box(18, 4, 10, 30, 13, 10, "fire").glow();
			m.box(20, 4, 10.5, 28, 14, 10.5, "fire").glow().turn('y', 22.5);
		} else {
			m.box(16, 2, 10, 32, 4.5, 12, "log");
			m.box(14, 1, 8.5, 34, 1.75, 14, "ash");
		}
		return m.done();
	}

	// --- on the walls ----------------------------------------------------------------------------------

	private static void walls(List<Piece> all) {
		piece(all, "velvet_curtain", "Velvet Curtain", Form.STACK, "walls").mount(Mount.WALL).feel(Feel.CLOTH)
				.model(FurnitureCatalog::curtain).skin("velvet", "f_curtain_red", "fringe", "f_gold_fringe");
		piece(all, "green_curtain", "Green Velvet Curtain", Form.STACK, "walls").mount(Mount.WALL).feel(Feel.CLOTH)
				.model(FurnitureCatalog::curtain).skin("velvet", "f_curtain_green", "fringe", "f_gold_fringe");
		piece(all, "torn_curtain", "Torn Curtain", Form.STACK, "walls").mount(Mount.WALL).feel(Feel.CLOTH)
				.model(FurnitureCatalog::curtain).skin("velvet", "f_curtain_torn", "fringe", "f_gold_fringe_torn");
		piece(all, "portrait", "Portrait", Form.MULTI, "walls").size(1, 2, 1).mount(Mount.WALL).looks(4)
				.model(l -> portrait(l.look())).skin("gilt", "f_gilt");
		piece(all, "seascape", "Painting of the Bay", Form.MULTI, "walls").size(2, 1, 1).mount(Mount.WALL)
				.model(l -> seascape()).skin("gilt", "f_gilt");
		piece(all, "wall_mirror", "Gilt Mirror", Form.MULTI, "walls").size(1, 2, 1).mount(Mount.WALL).feel(Feel.GLASS)
				.model(l -> mirror()).skin("gilt", "f_gilt");
		piece(all, "wall_clock", "Wall Clock", Form.SINGLE, "walls").mount(Mount.WALL).model(l -> wallClock()).skin("wood", "f_mahogany");
		piece(all, "notice_board", "Notice Board", Form.SINGLE, "walls").mount(Mount.WALL).model(l -> noticeBoard()).skin("wood", "f_oak");
		piece(all, "radiator", "Radiator", Form.SINGLE, "walls").feel(Feel.METAL).model(l -> radiator()).skin("iron", "f_iron_painted");
		piece(all, "light_switch", "Light Switch", Form.SINGLE, "walls").mount(Mount.WALL).feel(Feel.METAL).kind("switch")
				.model(FurnitureCatalog::lightSwitch).skin("pearl", "f_pearl", "black", "f_bakelite");
		piece(all, "lift_control", "Lift Operator's Lever", Form.SINGLE, "walls").mount(Mount.WALL).feel(Feel.METAL).kind("lift_control")
				.model(l -> liftControl()).skin("dial", "f_lift_dial", "black", "f_bakelite");
		piece(all, "lift_button", "Lift Call Button", Form.SINGLE, "walls").mount(Mount.WALL).feel(Feel.METAL).kind("lift_button")
				.model(l -> liftButton()).skin("pearl", "f_pearl");
	}

	/**
	 * Heavy velvet drawn across a window, in deep folds, at the front of its block: the window's
	 * recess is behind it. A pelmet with a gold fringe at the top, pooling on the floor at the foot.
	 */
	private static List<Box> curtain(Look l) {
		M m = new M();
		for (int i = 0; i < 4; i++) {
			double z = i % 2 == 0 ? 0.6 : 1.6;
			m.box(i * 4, l.lowEnd() ? 1 : 0, z, i * 4 + 4, l.highEnd() ? 12 : 16, z + 1.4, "velvet");
		}
		if (l.highEnd()) {
			m.box(-0.5, 12, 0, 16.5, 16, 3.4, "velvet").fit();
			m.box(-0.5, 10.5, 0, 16.5, 12, 3.4, "fringe").only(NORTH, EAST, WEST);
		}
		if (l.lowEnd()) {
			m.box(0, 0, 0, 16, 1, 3.8, "velvet");
		}
		return m.done();
	}

	/** A portrait in oils, two blocks high, in a gilt frame. */
	private static List<Box> portrait(int look) {
		M m = new M();
		m.box(1, 1.5, 14.5, 15, 30.5, 16, "gilt").front("f_portrait_" + look).fit();
		return m.done();
	}

	private static List<Box> seascape() {
		M m = new M();
		m.box(1, 2.5, 14.5, 31, 14.5, 16, "gilt").front("f_seascape").fit();
		return m.done();
	}

	private static List<Box> mirror() {
		M m = new M();
		m.box(1.5, 2, 14.75, 14.5, 29, 16, "gilt").front("f_mirror").fit();
		m.box(4, 29, 15, 12, 31, 16, "gilt");
		return m.done();
	}

	/** A round wall clock, stopped, like every clock in the hotel, at a quarter to midnight. */
	private static List<Box> wallClock() {
		M m = new M();
		m.box(3, 4, 14, 13, 14, 16, "wood");
		m.box(4, 3, 14, 12, 15, 16, "wood");
		m.box(4, 4, 13.75, 12, 14, 14, "f_clock_face").fit();
		m.box(6.5, 0, 14.5, 9.5, 3, 16, "wood");
		return m.done();
	}

	private static List<Box> noticeBoard() {
		M m = new M();
		m.box(1, 3, 15, 15, 14, 16, "wood").front("f_cork").fit();
		return m.done();
	}

	/** A cast-iron radiator under a window, its sections painted cream and chipped. */
	private static List<Box> radiator() {
		M m = new M();
		for (double x = 1.5; x < 14.5; x += 2) {
			m.box(x, 1.5, 9.5, x + 1.5, 13, 14, "iron");
		}
		m.box(1, 12, 10.5, 15, 13.5, 13, "iron");
		m.box(1, 1.5, 10.5, 15, 3, 13, "iron");
		m.box(1.5, 0, 10.5, 3, 1.5, 13, "iron");
		m.box(13, 0, 10.5, 14.5, 1.5, 13, "iron");
		m.box(14.5, 3, 11.25, 16, 4.5, 12.25, "f_iron");
		return m.done();
	}

	/**
	 * The lift operator's lever: a brass plate with a dial of the floors over a handle. Push it up
	 * and the car goes up a floor; down, down a floor.
	 */
	private static List<Box> liftControl() {
		M m = new M();
		m.box(4, 2, 15, 12, 14, 16, "brass");
		m.box(5, 9, 14.75, 11, 13, 15, "dial").only(NORTH).fit();
		m.box(7.5, 4.5, 13, 8.5, 9.5, 15, "brass").turn('z', 22.5, 8, 5, 14);
		m.box(7, 9, 12.5, 9, 10.5, 14, "black").turn('z', 22.5, 8, 5, 14);
		m.box(6.5, 3.5, 14, 9.5, 5.5, 15, "brass");
		return m.done();
	}

	/** A lift's call button: a pearl button in a brass plate, by the gate. */
	private static List<Box> liftButton() {
		M m = new M();
		m.box(6, 5, 15, 10, 11, 16, "brass");
		m.box(7, 7, 14.4, 9, 9, 15, "pearl");
		return m.done();
	}

	/** A brass push-button switch plate: one button in, the other out, as it's switched. */
	private static List<Box> lightSwitch(Look l) {
		M m = new M();
		m.box(5.5, 4, 15, 10.5, 12, 16, "brass");
		boolean on = l.lit();
		m.box(7, 8.5, on ? 14.6 : 14, 9, 10.5, 15, "pearl");
		m.box(7, 5.5, on ? 14 : 14.6, 9, 7.5, 15, "black");
		return m.done();
	}

	// --- on tables -------------------------------------------------------------------------------------

	private static void tabletop(List<Piece> all) {
		piece(all, "place_setting", "Place Setting", Form.SINGLE, "tabletop").soft().feel(Feel.GLASS).model(l -> placeSetting())
				.skin("china", "f_porcelain", "silver", "f_silver", "glass", "f_glassware", "napkin", "f_linen");
		piece(all, "bottles", "Bottles", Form.SINGLE, "tabletop").soft().feel(Feel.GLASS).model(l -> bottles())
				.skin("green", "f_bottle_green", "amber", "f_bottle_amber");
		piece(all, "open_ledger", "Open Ledger", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).model(l -> ledger())
				.skin("cover", "f_leather_brown");
		// Puzzle 1: the night clerk's guest ledger on the reception desk, to be read.
		piece(all, "night_ledger", "Guest Ledger", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:night_ledger")
				.model(l -> ledger()).skin("cover", "f_leather_brown");
		piece(all, "typewriter", "Typewriter", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).model(l -> typewriter())
				.skin("iron", "f_iron", "keys", "f_typewriter_keys", "paper", "f_paper");
		piece(all, "telephone", "Telephone", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).model(l -> telephone())
				.skin("black", "f_bakelite");
		piece(all, "service_bell", "Service Bell", Form.SINGLE, "tabletop").soft().noFacing().feel(Feel.METAL).model(l -> bell())
				.skin("wood", "f_mahogany");
		piece(all, "cash_register", "Cash Register", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).model(l -> register())
				.skin("front", "f_cash_register");
		piece(all, "gramophone", "Gramophone", Form.SINGLE, "tabletop").soft().feel(Feel.WOOD).model(l -> gramophone())
				.skin("wood", "f_mahogany", "record", "f_record");
		piece(all, "vase_of_flowers", "Vase of Dead Flowers", Form.SINGLE, "tabletop").soft().noFacing().feel(Feel.GLASS)
				.model(l -> vase()).skin("china", "f_porcelain", "flowers", "f_dead_flowers");
		piece(all, "books", "Pile of Books", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).model(l -> books()).skin("book", "f_books");
	}

	private static List<Box> placeSetting() {
		M m = new M();
		m.box(4, 0, 3, 12, 0.3, 11, "china");
		m.box(5, 0.3, 4, 11, 0.6, 10, "china");
		m.box(2.25, 0, 3.5, 3, 0.25, 10.5, "silver");
		m.box(13, 0, 3.5, 13.75, 0.25, 10.5, "silver");
		m.box(6.5, 0.6, 5, 9.5, 2.5, 8, "napkin");
		m.box(11, 0, 12, 12.5, 0.3, 13.5, "glass");
		m.box(11.5, 0.3, 12.5, 12, 2, 13, "glass");
		m.box(10.75, 2, 11.75, 12.75, 4.5, 13.75, "glass");
		return m.done();
	}

	private static List<Box> bottles() {
		M m = new M();
		round(m, 5, 6, 0, 7, 2, "green");
		m.box(4.25, 7, 5.25, 5.75, 10.5, 6.75, "green");
		round(m, 10.5, 7, 0, 6, 1.75, "amber");
		m.box(9.9, 6, 6.4, 11.1, 9, 7.6, "amber");
		round(m, 7.5, 11, 0, 8, 1.75, "green");
		m.box(6.9, 8, 10.4, 8.1, 11.5, 11.6, "green");
		return m.done();
	}

	private static List<Box> ledger() {
		M m = new M();
		m.box(2, 0, 4, 14, 0.5, 12.5, "cover");
		m.box(2.5, 0.5, 4.5, 7.9, 1.25, 12, "f_pages").top("f_pages_left").fit();
		m.box(8.1, 0.5, 4.5, 13.5, 1.25, 12, "f_pages").top("f_pages_right").fit();
		m.box(13, 0, 6, 14.5, 0.75, 9, "f_ink");
		return m.done();
	}

	private static List<Box> typewriter() {
		M m = new M();
		m.box(3, 0, 4, 13, 3, 12.5, "iron");
		m.box(3.5, 3, 3.5, 12.5, 4, 8.5, "iron").top("keys").fit();
		m.box(2, 4, 10, 14, 6.5, 12.5, "iron");
		m.box(1, 4.5, 10.75, 2, 5.5, 11.75, "iron");
		m.box(5, 5, 11, 11, 11, 11.5, "paper").fit();
		return m.done();
	}

	private static List<Box> telephone() {
		M m = new M();
		round(m, 7, 8, 0, 1, 2.5, "black");
		m.box(6.5, 1, 7.5, 7.5, 8, 8.5, "black");
		m.box(6, 7.5, 5.5, 8, 9.5, 7.5, "black");
		m.box(6.75, 8.25, 7.5, 7.25, 8.75, 8.5, "black");
		m.box(8.5, 3.5, 7.5, 9.5, 8, 8.5, "black");
		m.box(9.5, 6, 7.5, 10.5, 7, 8.5, "brass");
		return m.done();
	}

	// --- the puzzles' pieces ----------------------------------------------------------------------

	/**
	 * The safe behind the reception desk (puzzle 1, hotel design 4.1): a squat strongbox of painted
	 * iron two blocks wide, four brass dials across its door with a little enamel window over each
	 * for the figure it's turned to (drawn by its renderer). Its looks: shut; open, with what the
	 * night clerk left in it; open and empty.
	 */
	static List<Box> safe(int look) {
		M m = new M();
		m.box(0.5, 0, 1, 31.5, 1, 16, "iron");
		m.box(0.5, 14.5, 1, 31.5, 15.5, 16, "iron");
		if (look == 0) {
			m.box(1, 1, 1.5, 31, 14.5, 16, "iron");
			// Its pair of doors, brass-edged, meeting in the middle.
			m.box(3, 2.5, 0.6, 29, 13.5, 1.5, "iron");
			m.box(3, 2.5, 0.4, 29, 3, 0.6, "brass");
			m.box(3, 13, 0.4, 29, 13.5, 0.6, "brass");
			m.box(3, 3, 0.4, 3.5, 13, 0.6, "brass");
			m.box(28.5, 3, 0.4, 29, 13, 0.6, "brass");
			m.box(15.8, 3, 0.4, 16.2, 13, 0.6, "brass");
			for (double c : HotelFurniture.SAFE_DIALS) {
				m.box(c - 1.5, 6, -0.4, c + 1.5, 8, 0.6, "brass");
				m.box(c - 1, 5.5, -0.4, c + 1, 8.5, 0.6, "brass");
				m.box(c - 0.4, 6.6, -1, c + 0.4, 7.4, -0.4, "brass");
				m.box(c - 1.3, 9.5, 0.2, c + 1.3, 12, 0.6, "plate");
			}
			for (double x : new double[] {2.2, 29}) {
				m.box(x, 4, 0.6, x + 0.8, 6, 1.4, "brass");
				m.box(x, 10, 0.6, x + 0.8, 12, 1.4, "brass");
			}
			return m.done();
		}
		// Open: the frame round the doorway, the dark inside, a shelf, and the two doors swung right back on their hinges.
		m.box(1, 1, 1.5, 3, 14.5, 16, "iron");
		m.box(29, 1, 1.5, 31, 14.5, 16, "iron");
		m.box(3, 1, 1.5, 29, 2.5, 16, "iron");
		m.box(3, 13.5, 1.5, 29, 14.5, 16, "iron");
		m.box(3, 2.5, 14.5, 29, 13.5, 16, "dark");
		m.box(3, 7.5, 3, 29, 8, 14.5, "iron");
		for (double x : new double[] {2, 29}) {
			m.box(x, 2.5, -12, x + 1, 13.5, 1, "iron");
			m.box(x - 0.2, 2.5, -12, x + 1.2, 3, -11.6, "brass");
			m.box(x - 0.2, 13, -12, x + 1.2, 13.5, -11.6, "brass");
		}
		if (look == 1) {
			// The crank on the shelf, the keyring by it, the clerk's pages below.
			m.box(6, 8, 7, 14, 9, 8, "f_iron");
			m.box(13, 8, 7, 14, 9, 11, "f_iron");
			m.box(13, 9, 10, 14, 12, 11, "f_mahogany");
			m.box(18, 8, 6, 22, 8.4, 6.6, "brass");
			m.box(18, 8, 9.4, 22, 8.4, 10, "brass");
			m.box(18, 8, 6, 18.6, 8.4, 10, "brass");
			m.box(21.4, 8, 6, 22, 8.4, 10, "brass");
			m.box(20, 8, 9, 21, 8.3, 13, "brass");
			m.box(9, 2.5, 4, 17, 3.2, 10, "paper");
		}
		return m.done();
	}

	// --- things to carry, lying where they were left ----------------------------------------------

	/**
	 * The things to carry (hotel design 8, step 4d) as they lie on a table or the floor: picked up
	 * with a click, the block goes and the item is in your hands ({@code kind} "pickup:" and the
	 * item's id).
	 */
	private static void carried(List<Piece> all) {
		piece(all, "flashlight_lying", "Flashlight", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).kind("pickup:flashlight")
				.model(l -> flashlight()).skin("nickel", "f_silver", "lens", "f_lantern_glass");
		piece(all, "fuse_lying", "Fuse", Form.SINGLE, "tabletop").soft().feel(Feel.STONE).kind("pickup:fuse").model(l -> fuseLying())
				.skin("porcelain", "f_porcelain");
		// Puzzle 2, the Generator (hotel design 4.2).
		piece(all, "generator", "Generator", Form.MULTI, "service").size(3, 2, 2).feel(Feel.METAL).kind("puzzle:generator")
				.model(l -> generator()).skin("engine", "f_safe", "plinth", "f_brick_soot", "gauge", "f_clock_face");
		piece(all, "gen_primer", "Fuel Pump", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).kind("puzzle:primer")
				.model(l -> primer());
		piece(all, "gen_choke", "Choke", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).looks(2).kind("puzzle:choke")
				.model(l -> choke(l.look() == 1)).skin("knob", "f_leather_oxblood");
		piece(all, "gen_crank", "Starting Crank", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).kind("puzzle:crank")
				.model(l -> startingCrank());
		piece(all, "fuse_board", "Fuse Board", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).looks(4).kind("puzzle:fuse_board")
				.model(l -> fuseBoard(l.look())).skin("board", "f_enamel_grimy", "porcelain", "f_porcelain");
		piece(all, "bar_till", "Bar Till", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).looks(2).kind("puzzle:till")
				.model(l -> till(l.look() == 1)).skin("front", "f_cash_register");
		piece(all, "engineers_log", "Engine Room Log", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:engineers_log")
				.model(l -> ledger()).skin("cover", "f_leather_tan");
		// What the night porter left in the cold room, and what was done to him.
		piece(all, "porter_remains", "Remains", Form.SINGLE, "debris").soft().feel(Feel.CLOTH).model(l -> remains(true))
				.skin("bone", "f_bone", "coat", "f_coat", "blood", "f_old_blood");
		piece(all, "old_blood", "Old Blood", Form.SINGLE, "debris").soft().noFacing().feel(Feel.CLOTH).model(l -> flat("f_old_blood"));
		// Puzzle 3, the Last Waltz (hotel design 4.3).
		piece(all, "ballroom_piano", "Ballroom Grand", Form.MULTI, "things").size(2, 1, 3).kind("puzzle:piano").model(l -> ballroomPiano())
				.skin("black", "f_piano_black", "keys", "f_piano_keys8");
		piece(all, "bandstand_drawer", "Bandstand Drawer", Form.SINGLE, "things").looks(3).kind("puzzle:drawer")
				.model(l -> bandstandDrawer(l.look())).skin("dark", "f_soot");
		piece(all, "bandleader_letter", "A Letter", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:bandleader_letter")
				.model(l -> letter()).skin("paper", "f_paper");
		piece(all, "waltz_stand", "Music Stand", Form.SINGLE, "things").feel(Feel.METAL).kind("read:last_waltz").model(l -> musicStand())
				.skin("iron", "f_iron", "music", "f_sheet_music");
		// Puzzle 4, the Collector's Shelves (hotel design 4.4): the catalogue, its six volumes lying
		// about the library, and in the hidden study the book on No. 7, the code card and the key.
		piece(all, "cabinet_catalogue", "The Cabinet: A Catalogue", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH)
				.kind("read:cabinet_catalogue").model(l -> ledger()).skin("cover", "f_leather_green");
		String[] bindings = {"f_leather_oxblood", "f_leather_green", "f_leather_brown", "f_leather_tan", "f_velvet_red", "f_baize"};
		for (int i = 0; i < 6; i++) {
			piece(all, "catalogue_volume_" + i, "A Volume", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("pickup:volume:" + i)
					.model(l -> volume()).skin("cover", bindings[i]);
		}
		piece(all, "no_seven", "No. 7", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:no_seven").model(l -> ledger())
				.skin("cover", "f_leather_oxblood");
		piece(all, "code_card", "A Card", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:code_card").model(l -> card())
				.skin("paper", "f_paper");
		piece(all, "penthouse_key_lying", "Penthouse Lift Key", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).kind("pickup:penthouse_key")
				.model(l -> keyLying());
		// Puzzle 5: his journal on the study desk; in the control room the end of it, the code's second
		// half, the lockdown key, and him.
		piece(all, "wennberg_journal", "Journal", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:wennberg_journal")
				.model(l -> ledger()).skin("cover", "f_leather_oxblood");
		piece(all, "wennberg_last_pages", "Journal", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:wennberg_last_pages")
				.model(l -> ledger()).skin("cover", "f_leather_oxblood");
		piece(all, "code_card_2", "A Card", Form.SINGLE, "tabletop").soft().feel(Feel.CLOTH).kind("read:code_card_2").model(l -> card())
				.skin("paper", "f_paper");
		piece(all, "lockdown_key_lying", "Lockdown Key", Form.SINGLE, "tabletop").soft().feel(Feel.METAL).kind("pickup:lockdown_key")
				.model(l -> keyLying()).skin("f_silver", "f_iron", "brass", "f_leather_oxblood");
		piece(all, "wennberg_remains", "Remains", Form.SINGLE, "debris").soft().feel(Feel.CLOTH).model(l -> remains(false))
				.skin("bone", "f_bone", "coat", "f_dust_sheet", "blood", "f_old_blood");
		// The finale (hotel design 4.6): the lockdown's release panel in the night clerk's office, and its lever.
		piece(all, "release_panel", "Lockdown Release", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).looks(3)
				.kind("puzzle:release_panel").model(l -> releasePanel(l.look())).skin("plate", "f_safe", "button", "f_porcelain");
		piece(all, "release_lever", "Release Lever", Form.SINGLE, "service").mount(Mount.WALL).feel(Feel.METAL).looks(2)
				.kind("puzzle:release_lever").model(l -> releaseLever(l.look() == 1)).skin("plate", "f_safe", "knob", "f_leather_oxblood");
		piece(all, "reception_safe", "Reception Safe", Form.MULTI, "storage").size(2, 1, 1).feel(Feel.METAL).looks(3).kind("safe")
				.model(l -> safe(l.look())).skin("iron", "f_safe", "plate", "f_enamel", "dark", "f_soot", "paper", "f_paper");
	}

	/** A cartridge fuse on its side: a porcelain barrel, brass caps. */
	private static List<Box> fuseLying() {
		M m = new M();
		m.box(5.5, 0, 7, 10.5, 1.8, 8.8, "porcelain");
		m.box(4, 0, 6.8, 5.5, 2, 9, "brass");
		m.box(10.5, 0, 6.8, 12, 2, 9, "brass");
		m.box(3.5, 0.6, 7.5, 4, 1.4, 8.3, "brass");
		m.box(12, 0.6, 7.5, 12.5, 1.4, 8.3, "brass");
		return m.done();
	}

	/**
	 * The generator (puzzle 2): a gas engine and its dynamo on a brick plinth, three blocks long,
	 * enamelled the darkest green and banded in brass, the flywheel between them, its exhaust going
	 * up through the ceiling, gauges on its front. Its controls are pieces of their own, hung on its
	 * front: the fuel pump, the choke, the starting crank.
	 */
	private static List<Box> generator() {
		M m = new M();
		m.box(0, 0, 1, 48, 3, 31, "plinth");
		// The engine: block, head, rocker covers.
		m.box(2, 3, 4, 28, 20, 28, "engine");
		m.box(4, 20, 6, 26, 25, 26, "engine");
		for (double x : new double[] {6, 13, 20}) {
			m.box(x, 25, 9, x + 5, 27, 23, "brass");
		}
		m.box(2, 11, 3.5, 28, 12, 28.5, "brass");
		// The gauges on its front.
		for (double x : new double[] {7, 15}) {
			m.box(x, 14, 3, x + 4, 18, 4, "brass");
			m.box(x + 0.5, 14.5, 2.8, x + 3.5, 17.5, 3, "gauge").fit();
		}
		// The flywheel.
		m.box(29, 3, 6, 32, 27, 26, "f_iron");
		m.box(29, 6, 3, 32, 24, 29, "f_iron");
		m.box(28, 13, 14, 33, 16, 18, "brass");
		// The dynamo, banded in brass.
		m.box(33, 3, 8, 46, 17, 24, "engine");
		for (double x : new double[] {35, 43}) {
			m.box(x, 2.8, 7.6, x + 1, 17.4, 24.4, "brass");
		}
		m.box(40, 17, 13, 44, 19, 19, "f_iron");
		// The exhaust, up through the ceiling.
		m.box(18, 25, 22, 21, 32, 25, "f_iron");
		return m.done();
	}

	/** The generator's fuel pump: a brass barrel on a bracket, its plunger and T-handle on top. */
	private static List<Box> primer() {
		M m = new M();
		m.box(5, 3, 14.5, 11, 4, 16, "f_iron");
		m.box(6, 3, 11, 10, 11, 15, "brass");
		m.box(5.5, 10.5, 10.5, 10.5, 11.5, 15.5, "brass");
		m.box(7.5, 11.5, 12.5, 8.5, 14.5, 13.5, "f_iron");
		m.box(4.5, 14.5, 12, 11.5, 15.5, 14, "f_mahogany");
		m.box(7, 1, 12.5, 9, 3, 13.5, "f_iron");
		return m.done();
	}

	/** The choke: a quadrant of iron, its lever down (shut) or up (open), a red knob on the end. */
	private static List<Box> choke(boolean open) {
		M m = new M();
		m.box(3, 3, 15, 13, 13, 16, "f_iron");
		m.box(7, 7, 14, 9, 9, 15, "brass");
		if (open) {
			m.box(7.5, 8, 13, 8.5, 14.5, 14, "f_iron");
			m.box(6.75, 14, 12.5, 9.25, 16, 14.5, "knob");
		} else {
			m.box(7.5, 1.5, 13, 8.5, 8, 14, "f_iron");
			m.box(6.75, 0, 12.5, 9.25, 2, 14.5, "knob");
		}
		return m.done();
	}

	/** The starting crank: a boss on the shaft, the iron arm, the wooden grip standing out from it. */
	private static List<Box> startingCrank() {
		M m = new M();
		m.box(5, 5, 14, 11, 11, 16, "f_iron");
		m.box(6, 6, 12.5, 10, 10, 14, "brass");
		m.box(7.5, 7.5, 12.5, 14, 8.5, 13.5, "f_iron");
		m.box(13, 7, 8, 14.5, 9, 13.5, "f_mahogany");
		return m.done();
	}

	/** The fuse board: an enamelled board, three porcelain holders, as many fuses in them as the look says. */
	private static List<Box> fuseBoard(int fuses) {
		M m = new M();
		m.box(1, 1, 15, 15, 15, 16, "board");
		m.box(1, 1, 14.6, 15, 1.6, 15, "brass");
		m.box(1, 14.4, 14.6, 15, 15, 15, "brass");
		for (int i = 0; i < 3; i++) {
			double x = 3 + i * 4;
			m.box(x, 4, 14, x + 2, 12, 15, "porcelain");
			if (i < fuses) {
				m.box(x + 0.25, 5, 13, x + 1.75, 11, 14, "porcelain");
				m.box(x, 4.5, 12.8, x + 2, 6, 14, "brass");
				m.box(x, 10, 12.8, x + 2, 11.5, 14, "brass");
			} else {
				m.box(x + 0.6, 5, 13.6, x + 1.4, 6, 14, "brass");
				m.box(x + 0.6, 10, 13.6, x + 1.4, 11, 14, "brass");
			}
		}
		m.box(6, 2, 13.5, 10, 3, 15, "f_iron");
		return m.done();
	}

	/**
	 * The ballroom's grand piano (puzzle 3): the grand piano, its keyboard across the whole of its
	 * front, eight broad keys an octave from C, so each can be played with a click.
	 */
	private static List<Box> ballroomPiano() {
		M m = new M();
		for (Box b : grandPiano()) {
			// Its own keyboard instead of the other's.
			if (b.z1() <= 0.5 && b.y0() >= 9 && b.y1() <= 13) {
				continue;
			}
			m.add(List.of(b), 0, 0, 0);
		}
		m.box(0.5, 9, -4, 31.5, 10.5, 0, "black");
		m.box(0.5, 10.5, -4, 31.5, 11, -0.5, "keys").top("keys").fit();
		m.box(0.5, 11, -0.5, 31.5, 13, 0.5, "black");
		return m.done();
	}

	/**
	 * The drawer in the front of the bandstand (puzzle 3): a block of its parquet, the panel of a
	 * drawer let into its face; open, the dark inside, Wennberg's key in it or not.
	 */
	private static List<Box> bandstandDrawer(int look) {
		M m = new M();
		if (look == 0) {
			m.box(0, 0, 0, 16, 16, 16, "rauk:block/parquet");
			m.box(3, 5, -0.2, 13, 5.5, 0, "brass");
			m.box(3, 10.5, -0.2, 13, 11, 0, "brass");
			m.box(3, 5.5, -0.2, 3.5, 10.5, 0, "brass");
			m.box(12.5, 5.5, -0.2, 13, 10.5, 0, "brass");
			m.box(7.5, 7.5, -0.4, 8.5, 8.5, 0, "brass");
			return m.done();
		}
		m.box(0, 0, 0, 16, 5, 16, "rauk:block/parquet");
		m.box(0, 11, 0, 16, 16, 16, "rauk:block/parquet");
		m.box(0, 5, 0, 3, 11, 16, "rauk:block/parquet");
		m.box(13, 5, 0, 16, 11, 16, "rauk:block/parquet");
		m.box(3, 5, 14, 13, 11, 16, "dark");
		// The drawer, pulled out.
		m.box(3, 5, -6, 13, 5.5, 4, "rauk:block/parquet");
		m.box(3, 5.5, -6, 3.5, 8, 4, "rauk:block/parquet");
		m.box(12.5, 5.5, -6, 13, 8, 4, "rauk:block/parquet");
		m.box(3.5, 5.5, -6, 12.5, 8, -5.5, "rauk:block/parquet");
		if (look == 1) {
			m.box(6, 5.5, -3, 7.5, 6, 1, "brass");
			m.box(5.5, 5.5, -4.5, 8, 6.2, -2.5, "brass");
			m.box(7.5, 5.5, 0, 8.5, 6, 0.6, "brass");
		}
		return m.done();
	}

	/** A book lying shut: its binding, the edge of its pages. */
	private static List<Box> volume() {
		M m = new M();
		m.box(4, 0, 3, 12, 0.5, 13, "cover");
		m.box(4.3, 0.5, 3.3, 11.6, 2, 12.7, "f_pages");
		m.box(4, 2, 3, 12, 2.5, 13, "cover");
		m.box(4, 0, 3, 4.6, 2.5, 13, "cover");
		return m.done();
	}

	/** A card, lying a little askew. */
	private static List<Box> card() {
		M m = new M();
		m.box(5, 0, 5, 11, 0.2, 9, "paper").turn('y', 22.5);
		return m.done();
	}

	/** A key on a brass tag, lying. */
	private static List<Box> keyLying() {
		M m = new M();
		m.box(5, 0, 7, 11, 0.6, 8, "f_silver");
		m.box(3, 0, 6, 5, 0.6, 9, "f_silver");
		m.box(9.5, 0, 8, 10.3, 0.6, 9, "f_silver");
		m.box(10.5, 0, 8, 11, 0.6, 9.2, "f_silver");
		m.box(1, 0, 9, 3.5, 0.4, 12, "brass");
		return m.done();
	}

	/** Where the release panel's keys are: the middles of its three columns (from its own left) and four rows (from the top), in sixteenths. */
	public static final double[] PANEL_COLUMNS = {11, 8, 5}, PANEL_ROWS = {9.9, 7.9, 5.9, 3.9};

	/**
	 * The lockdown's release panel (the finale): an enamelled plate, a dark window over a keypad of
	 * porcelain keys (1 to 9, then clear and 0), the keyhole at its foot with the key in it or not,
	 * a lamp that lights when the panel is ready.
	 */
	private static List<Box> releasePanel(int look) {
		M m = new M();
		m.box(1.5, 1, 15, 14.5, 15, 16, "plate");
		m.box(1.5, 1, 14.8, 14.5, 1.4, 15, "brass");
		m.box(1.5, 14.6, 14.8, 14.5, 15, 15, "brass");
		m.box(3, 11.6, 14.6, 13, 13.8, 15, "f_soot");
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 3; col++) {
				if (row == 3 && col == 2) {
					continue;
				}
				double x = PANEL_COLUMNS[col], y = PANEL_ROWS[row];
				m.box(x - 1, y - 0.7, 14.3, x + 1, y + 0.7, 15, row == 3 && col == 0 ? "f_leather_oxblood" : "button");
			}
		}
		m.box(2, 2, 14.5, 4, 4.5, 15, "brass");
		m.box(2.7, 2.6, 14.3, 3.3, 3.8, 14.5, "f_soot");
		if (look >= 1) {
			m.box(2.6, 3.2, 12.6, 3.4, 4.6, 14.3, "f_iron");
			m.box(2.2, 4.6, 12.8, 3.8, 6, 13.6, "f_iron");
		}
		m.box(12, 2.2, 14.3, 13.4, 3.6, 15, look == 2 ? "f_bulb_lit" : "f_bottle_green");
		return m.done();
	}

	/** The release lever: a big knife switch on an enamelled plate, up (set) or down (pulled). */
	private static List<Box> releaseLever(boolean pulled) {
		M m = new M();
		m.box(5, 1, 15, 11, 15, 16, "plate");
		m.box(6.5, 7, 14, 9.5, 9, 15, "brass");
		if (pulled) {
			m.box(7.5, 1.5, 13, 8.5, 8.5, 14, "f_iron");
			m.box(6.5, 0, 12, 9.5, 2, 14.5, "knob");
		} else {
			m.box(7.5, 7.5, 13, 8.5, 14.5, 14, "f_iron");
			m.box(6.5, 14, 12, 9.5, 16, 14.5, "knob");
		}
		return m.done();
	}

	/** A letter: two sheets, folded open. */
	private static List<Box> letter() {
		M m = new M();
		m.box(4, 0, 4, 12, 0.3, 14, "paper").turn('y', 22.5);
		m.box(5, 0.3, 3, 11.5, 0.5, 11, "paper").turn('y', -22.5);
		return m.done();
	}

	/** The bar's till: the cash register, its drawer shut or pulled out (puzzle 2: a fuse in it). */
	private static List<Box> till(boolean open) {
		M m = new M();
		m.add(register(), 0, 0, 0);
		if (open) {
			m.box(4, 0.5, 1, 12, 3, 4, "brass");
			m.box(4.5, 2.6, 1.5, 11.5, 3, 3.5, "f_iron");
		}
		return m.done();
	}

	/** What is left of the night porter: his skull, ribs, long bones, his coat torn open, his cap, a dried stain round it all. */
	private static List<Box> remains(boolean porter) {
		M m = new M();
		if (porter) {
			m.box(0, 0, 0, 16, 0.1, 16, "blood").only(UP);
		}
		// The coat, torn open, flat on the floor.
		m.box(1, 0.1, 4, 14, 0.7, 15, "coat");
		m.box(9, 0.1, 13, 15.5, 0.6, 16, "coat").turn('y', -22.5);
		// The skull, its sockets and the jaw beside it.
		m.box(1.5, 0.1, 0.5, 6, 4.2, 5, "bone");
		m.box(2.3, 1.8, 0.4, 3.5, 3, 0.5, "f_soot");
		m.box(4, 1.8, 0.4, 5.2, 3, 0.5, "f_soot");
		m.box(2, 0.1, 5.2, 5.5, 1, 6.2, "bone");
		// The ribs over the spine, a long bone and an arm flung out.
		for (int i = 0; i < 5; i++) {
			m.box(5.5 + i * 1.6, 0.7, 5.5, 6.3 + i * 1.6, 2, 12.5, "bone");
		}
		m.box(5.5, 0.7, 8.6, 14, 1.5, 9.4, "bone");
		m.box(8, 0.1, 13, 15, 1, 14.2, "bone").turn('y', 22.5);
		m.box(0.5, 0.1, 9, 6, 0.9, 10, "bone").turn('y', -22.5);
		if (porter) {
			// His cap, the brass band round it.
			m.box(11, 0.1, 0.5, 15.5, 2.4, 5, "f_coat");
			m.box(10.8, 0.9, 0.3, 15.7, 1.4, 5.2, "brass");
			m.box(10.5, 0.1, 4.5, 16, 0.5, 6.5, "f_coat");
		} else {
			// A pocket watch, its chain across the ribs.
			m.box(12, 0.1, 2, 14, 0.6, 4, "brass");
			m.box(9, 2, 8.8, 12.5, 2.2, 9.2, "brass");
		}
		return m.done();
	}

	/** A nickel-plated electric torch on its side: the tube, the wider head with its lens, the brass switch. */
	private static List<Box> flashlight() {
		M m = new M();
		m.box(3.5, 0.3, 7.1, 4, 2.3, 9.1, "nickel");
		m.box(4, 0, 6.9, 11, 2.6, 9.3, "nickel");
		m.box(11, 0, 6.5, 12.5, 3, 9.7, "nickel");
		m.box(12.5, 0, 6.3, 14, 3.4, 9.9, "nickel");
		m.box(14, 0.3, 6.6, 14.2, 3.1, 9.6, "lens").only(EAST);
		m.box(7, 2.6, 7.7, 8.5, 3.1, 8.5, "brass");
		return m.done();
	}

	private static List<Box> bell() {
		M m = new M();
		round(m, 8, 8, 0, 0.5, 2.5, "wood");
		round(m, 8, 8, 0.5, 1.75, 2, "brass");
		round(m, 8, 8, 1.75, 2.25, 1.25, "brass");
		m.box(7.75, 2.25, 7.75, 8.25, 3, 8.25, "brass");
		return m.done();
	}

	private static List<Box> register() {
		M m = new M();
		m.box(3, 0, 4, 13, 7, 13, "brass").front("front").fit();
		m.box(3.5, 7, 7, 12.5, 9, 13, "brass");
		m.box(5, 9, 9, 11, 12, 11, "brass").front("f_register_sign").fit();
		m.box(13, 3, 7, 14.5, 4, 8, "brass");
		return m.done();
	}

	private static List<Box> gramophone() {
		M m = new M();
		m.box(3, 0, 3, 13, 4, 13, "wood");
		round(m, 8, 8, 4, 4.4, 4.5, "record");
		m.box(11, 4, 10, 12, 6, 11, "brass");
		m.box(9, 5.5, 9.5, 11.5, 6, 10.5, "brass");
		m.box(10.5, 6, 9.5, 11.5, 9, 10.5, "brass");
		m.box(9.5, 8, 7, 12.5, 11, 10, "brass").turn('x', -22.5);
		m.box(8.5, 9, 3, 13.5, 14, 7.5, "brass").turn('x', -45);
		m.box(7, 10, -1, 15, 18, 3, "brass").turn('x', -45);
		return m.done();
	}

	private static List<Box> vase() {
		M m = new M();
		round(m, 8, 8, 0, 1, 2, "china");
		round(m, 8, 8, 1, 6, 2.75, "china");
		round(m, 8, 8, 6, 8, 1.75, "china");
		m.box(3, 6, 8, 13, 16, 8, "flowers").turn('y', 45);
		m.box(3, 6, 8, 13, 16, 8, "flowers").turn('y', -45);
		return m.done();
	}

	private static List<Box> books() {
		M m = new M();
		m.box(3, 0, 4, 13, 2, 12, "book");
		m.box(4, 2, 4.5, 12.5, 3.5, 11, "book").turn('y', 22.5);
		m.box(3.5, 3.5, 5, 11.5, 5.5, 11.5, "book").turn('y', -22.5);
		return m.done();
	}

	// --- standing about ----------------------------------------------------------------------------------

	private static void things(List<Piece> all) {
		piece(all, "globe", "Globe", Form.SINGLE, "things").model(l -> globe()).skin("wood", "f_mahogany", "map", "f_globe");
		piece(all, "ashtray_stand", "Smoker's Stand", Form.SINGLE, "things").noFacing().feel(Feel.METAL).model(l -> ashtrayStand());
		piece(all, "music_stand", "Music Stand", Form.SINGLE, "things").feel(Feel.METAL).model(l -> musicStand())
				.skin("iron", "f_iron", "music", "f_sheet_music");
		piece(all, "potted_palm", "Dead Palm", Form.MULTI, "things").size(1, 2, 1).noFacing().feel(Feel.METAL).model(l -> palm())
				.skin("soil", "f_soil", "frond", "f_palm_dead", "trunk", "f_palm_trunk");
		piece(all, "coat_stand", "Coat Stand", Form.MULTI, "things").size(1, 2, 1).model(l -> coatStand())
				.skin("wood", "f_mahogany", "coat", "f_coat", "hat", "f_bakelite");
		piece(all, "grandfather_clock", "Grandfather Clock", Form.MULTI, "things").size(1, 3, 1).model(l -> grandfatherClock())
				.skin("wood", "f_mahogany", "face", "f_clock_face");
		// Puzzle 5: the clock in Wennberg's study, its hands drawn by its renderer where they're set.
		piece(all, "study_clock", "Grandfather Clock", Form.MULTI, "things").size(1, 3, 1).kind("clock").model(l -> grandfatherClock())
				.skin("wood", "f_mahogany", "face", "f_clock_face_bare");
		piece(all, "grand_piano", "Grand Piano", Form.MULTI, "things").size(2, 1, 3).model(l -> grandPiano())
				.skin("black", "f_piano_black", "keys", "f_piano_keys");
		piece(all, "rocking_horse", "Rocking Horse", Form.SINGLE, "things").model(l -> rockingHorse())
				.skin("paint", "f_dapple", "wood", "f_mahogany", "mane", "f_horsehair");
		piece(all, "porcelain_doll", "Porcelain Doll", Form.SINGLE, "things").soft().feel(Feel.CLOTH).model(l -> doll())
				.skin("dress", "f_doll_dress", "face", "f_doll_face", "hair", "f_horsehair");
	}

	private static List<Box> globe() {
		M m = new M();
		m.box(3, 0, 7, 13, 1, 9, "wood");
		m.box(7, 0, 3, 9, 1, 13, "wood");
		m.box(7, 1, 7, 9, 6, 9, "wood");
		m.box(2, 5.5, 7.25, 3, 15, 8.75, "brass");
		m.box(13, 5.5, 7.25, 14, 15, 8.75, "brass");
		m.box(3, 5.5, 7.25, 13, 6.5, 8.75, "brass");
		m.box(4, 6.5, 5, 12, 14.5, 11, "map").turn('z', 22.5, 8, 10.5, 8);
		m.box(5, 5.5, 4, 11, 15.5, 12, "map").turn('z', 22.5, 8, 10.5, 8);
		m.box(3, 7.5, 6, 13, 13.5, 10, "map").turn('z', 22.5, 8, 10.5, 8);
		return m.done();
	}

	private static List<Box> ashtrayStand() {
		M m = new M();
		round(m, 8, 8, 0, 1, 3.5, "brass");
		m.box(7.5, 1, 7.5, 8.5, 11, 8.5, "brass");
		round(m, 8, 8, 11, 12, 3.5, "brass");
		round(m, 8, 8, 12, 12.5, 2.5, "f_ash");
		return m.done();
	}

	private static List<Box> musicStand() {
		M m = new M();
		m.box(7.5, 0, 7.5, 8.5, 12, 8.5, "iron");
		m.box(3, 0, 7.5, 13, 0.75, 8.5, "iron");
		m.box(7.5, 0, 3, 8.5, 0.75, 13, "iron");
		m.box(3, 11, 7, 13, 18, 7.5, "iron").front("music").fit().turn('x', 22.5, 8, 12, 7.5);
		m.box(3, 10.5, 6, 13, 11, 7.5, "iron").turn('x', 22.5, 8, 12, 7.5);
		return m.done();
	}

	// --- the reshaped hotel's rooms (hotel design 14) --------------------------------------------------

	private static void reshaped(List<Piece> all) {
		piece(all, "cinema_seat", "Cinema Seat", Form.RUN, "seating").feel(Feel.CLOTH).model(FurnitureCatalog::cinemaSeat)
				.skin("velvet", "f_velvet_red", "iron", "f_iron");
		piece(all, "theatre_organ", "Theatre Organ", Form.MULTI, "things").size(2, 2, 1).model(l -> organ())
				.skin("wood", "f_mahogany", "keys", "f_piano_keys", "stops", "f_bakelite", "pipe", "brass");
		piece(all, "organ_pipes", "Organ Pipes", Form.MULTI, "things").size(1, 3, 1).noFacing().feel(Feel.METAL).model(l -> organPipes())
				.skin("pipe", "brass");
		piece(all, "projector", "Film Projector", Form.SINGLE, "things").feel(Feel.METAL).model(l -> projector())
				.skin("body", "f_iron_painted", "reel", "f_iron", "lens", "brass");
		piece(all, "barber_chair", "Barber's Chair", Form.SINGLE, "seating").feel(Feel.METAL).model(l -> barberChair())
				.skin("leather", "f_leather_oxblood", "chrome", "f_silver", "base", "f_porcelain");
		piece(all, "barber_pole", "Barber's Pole", Form.MULTI, "things").size(1, 2, 1).noFacing().feel(Feel.GLASS).model(l -> barberPole())
				.skin("stripes", "f_barber_stripes", "cap", "brass");
		piece(all, "display_case", "Display Case", Form.RUN, "counters").model(FurnitureCatalog::displayCase)
				.skin("wood", "f_mahogany", "glass", "f_glass_shards", "velvet", "f_velvet_red", "silver", "f_silver");
		piece(all, "wicker_chair", "Wicker Chair", Form.SINGLE, "seating").feel(Feel.WOOD).model(l -> wickerChair())
				.skin("wicker", "f_wicker", "cushion", "f_linen_dirty");
		piece(all, "statue", "Statue", Form.MULTI, "things").size(1, 2, 1).feel(Feel.METAL).model(l -> statue())
				.skin("stone", "minecraft:block/calcite", "plinth", "minecraft:block/smooth_stone");
		piece(all, "film_reels", "Film Reels", Form.SINGLE, "things").soft().feel(Feel.METAL).model(l -> filmReels())
				.skin("reel", "f_iron", "film", "f_bakelite");
	}

	/** A tip-up seat in a row of them: red velvet on an iron standard, an arm between each two. */
	private static List<Box> cinemaSeat(Look l) {
		M m = new M();
		m.box(1.5, 5, 2.5, 14.5, 7, 11.5, "velvet");
		m.box(1.5, 7, 11, 14.5, 19, 13.5, "velvet");
		m.box(1.5, 18.5, 11.25, 14.5, 19.5, 13.75, "iron");
		m.box(0, 0, 3, 1.5, 9.5, 13, "iron");
		m.box(0, 9.5, 3, 1.5, 10.5, 12, "f_mahogany");
		if (l.highEnd()) {
			m.box(14.5, 0, 3, 16, 9.5, 13, "iron");
			m.box(14.5, 9.5, 3, 16, 10.5, 12, "f_mahogany");
		}
		return m.done();
	}

	/** The cinema's organ console: a mahogany case, two keyboards stepped one above the other, rows of stops. */
	private static List<Box> organ() {
		M m = new M();
		m.box(0, 0, 6, 32, 15, 16, "wood");
		m.box(1, 13, 0, 31, 14, 7, "wood");
		m.box(2, 14, 1, 30, 14.75, 5, "keys").top("keys").fit();
		m.box(1, 15, 5, 31, 16, 16, "wood");
		m.box(2, 16, 5, 30, 16.75, 9, "keys").top("keys").fit();
		m.box(0, 16, 9, 32, 30, 16, "wood");
		for (int x = 3; x <= 27; x += 3) {
			m.box(x, 18, 8.5, x + 1.5, 19.5, 9, "stops");
			m.box(x + 1, 21, 8.5, x + 2.5, 22.5, 9, "stops");
		}
		m.box(0, 29, 8, 32, 31, 16, "wood");
		m.box(4, 0, 0, 28, 1, 6, "wood");
		return m.done();
	}

	/** Organ pipes standing in a rank, the longest in the middle, tarnished. */
	private static List<Box> organPipes() {
		M m = new M();
		double[] heights = {30, 36, 42, 46, 42, 36, 30};
		for (int i = 0; i < heights.length; i++) {
			double x = 1 + i * 2;
			m.box(x, 0, 6.5, x + 1.75, heights[i], 8.25, "pipe");
			m.box(x, heights[i] * 0.2, 6, x + 1.75, heights[i] * 0.2 + 1.5, 6.5, "f_iron");
		}
		m.box(0, 0, 5, 16, 1.5, 10, "f_mahogany");
		return m.done();
	}

	/** A projector on its stand, two reels on top, the lens to the front. */
	private static List<Box> projector() {
		M m = new M();
		m.box(6.5, 0, 6.5, 9.5, 6, 9.5, "body");
		m.box(4, 0, 4, 12, 1, 12, "body");
		m.box(4, 6, 3, 12, 11, 13, "body");
		m.box(6.5, 7, 0, 9.5, 10, 3, "lens");
		m.box(7.25, 11, 1, 8.75, 19, 9, "reel");
		m.box(7.25, 11, 8, 8.75, 18, 15, "reel");
		return m.done();
	}

	/** A barber's chair: oxblood leather on a porcelain pedestal, a chrome footrest and headrest. */
	private static List<Box> barberChair() {
		M m = new M();
		round(m, 8, 8, 0, 1.5, 5, "base");
		m.box(6.5, 1.5, 6.5, 9.5, 6, 9.5, "chrome");
		m.box(2, 6, 3, 14, 9, 13, "leather");
		m.box(2, 9, 11, 14, 20, 13.5, "leather");
		m.box(5, 20, 11.5, 11, 23, 13, "leather");
		m.box(1, 9, 4, 2.5, 12, 12, "chrome");
		m.box(13.5, 9, 4, 15, 12, 12, "chrome");
		m.box(4, 2, -1, 12, 3, 3, "chrome");
		return m.done();
	}

	/** A barber's pole: a glass cylinder of red and white stripes between brass caps. */
	private static List<Box> barberPole() {
		M m = new M();
		round(m, 8, 8, 0, 2, 3, "cap");
		round(m, 8, 8, 2, 26, 2.25, "stripes");
		round(m, 8, 8, 26, 28, 3, "cap");
		m.box(7.25, 28, 7.25, 8.75, 30, 8.75, "cap");
		return m.done();
	}

	/** A jeweller's case: a mahogany counter with a glass top, smashed, the velvet trays inside empty. */
	private static List<Box> displayCase(Look l) {
		M m = new M();
		m.box(0, 0, 2, 16, 11, 14, "wood");
		m.box(0.5, 11, 2.5, 15.5, 12, 13.5, "velvet");
		m.box(0, 11, 2, 16, 16, 2.5, "glass");
		m.box(0, 15.5, 2, 16, 16, 14, "glass");
		m.box(0, 11, 13.5, 16, 16, 14, "glass");
		if (l.lowEnd()) {
			m.box(0, 11, 2, 0.5, 16, 14, "glass");
		}
		if (l.highEnd()) {
			m.box(15.5, 11, 2, 16, 16, 14, "glass");
		}
		m.box(4, 12, 6, 6, 12.5, 8, "silver");
		return m.done();
	}

	/** A palm court chair of wicker, its cushion gone grey. */
	private static List<Box> wickerChair() {
		M m = new M();
		m.box(2, 0, 2, 14, 7, 14, "wicker");
		m.box(2.5, 7, 2.5, 13.5, 8.5, 13.5, "cushion");
		m.box(2, 7, 12, 14, 19, 14, "wicker");
		m.box(2, 7, 2, 3.5, 12, 12, "wicker");
		m.box(12.5, 7, 2, 14, 12, 12, "wicker");
		return m.done();
	}

	/** A draped figure on a plinth, pale stone, its face worn away. */
	private static List<Box> statue() {
		M m = new M();
		m.box(1, 0, 1, 15, 7, 15, "plinth");
		m.box(0.5, 7, 0.5, 15.5, 8, 15.5, "plinth");
		m.box(4, 8, 5, 12, 22, 11, "stone");
		m.box(3.5, 8, 4, 12.5, 14, 12, "stone");
		m.box(5, 22, 6, 11, 26, 10, "stone");
		m.box(6, 26, 6.5, 10, 30, 9.5, "stone");
		m.box(11, 18, 6.5, 13.5, 26, 8.5, "stone").turn('z', -22.5, 12, 22, 7.5);
		m.box(2.5, 14, 6.5, 5, 22, 8.5, "stone");
		return m.done();
	}

	/** Film reels in their tins, stacked on the floor. */
	private static List<Box> filmReels() {
		M m = new M();
		round(m, 6, 6, 0, 1.5, 4.5, "reel");
		round(m, 6, 6, 1.5, 3, 4.5, "film");
		round(m, 11, 10, 0, 1.5, 4, "reel");
		return m.done();
	}

	/** A palm in a brass urn, long dead, its fronds brown and hanging. */
	private static List<Box> palm() {
		M m = new M();
		round(m, 8, 8, 0, 2, 3, "brass");
		round(m, 8, 8, 2, 8, 4, "brass");
		round(m, 8, 8, 8, 9.5, 4.75, "brass");
		round(m, 8, 8, 8.5, 9.25, 3.75, "soil");
		m.box(7, 9, 7, 9, 17, 9, "trunk");
		m.box(7.25, 17, 7.25, 8.75, 22, 8.75, "trunk");
		for (double a : new double[] {45, -45}) {
			m.box(0, 14, 8, 16, 30, 8, "frond").turn('y', a, 8, 22, 8);
		}
		m.box(8, 12, 0, 8, 28, 16, "frond").turn('z', 22.5, 8, 20, 8);
		m.box(0, 12, 8, 16, 28, 8, "frond").turn('x', -22.5, 8, 20, 8);
		return m.done();
	}

	private static List<Box> coatStand() {
		M m = new M();
		m.box(3, 0, 7.5, 13, 1, 8.5, "wood");
		m.box(7.5, 0, 3, 8.5, 1, 13, "wood");
		m.box(7.25, 1, 7.25, 8.75, 28, 8.75, "wood");
		round(m, 8, 8, 28, 29.5, 1.5, "wood");
		for (double[] h : new double[][] {{4, 7.5, 7, 8.5}, {8.75, 7.5, 12, 8.5}, {7.5, 4, 8.5, 7.25}, {7.5, 8.75, 8.5, 12}}) {
			m.box(h[0], 25, h[1], h[2], 26, h[3], "brass");
		}
		m.box(3, 10, 9, 7.5, 25, 12.5, "coat");
		m.box(2.5, 20, 8.5, 8, 25.5, 13, "coat");
		round(m, 12, 6, 26, 27, 2.5, "hat");
		round(m, 12, 6, 27, 29, 1.75, "hat");
		return m.done();
	}

	private static List<Box> grandfatherClock() {
		M m = new M();
		m.box(1.5, 0, 4, 14.5, 2, 15.5, "f_mahogany_dark");
		m.box(2, 2, 4.5, 14, 11, 15, "wood").front("f_mahogany_panel").fit();
		m.box(3, 11, 5.5, 13, 32, 14.5, "wood").front("f_clock_trunk").fit();
		m.box(2, 31, 4.5, 14, 33, 15, "wood");
		m.box(2.5, 33, 5, 13.5, 44, 14.5, "wood");
		m.box(3.5, 34.5, 4.75, 12.5, 43.5, 5, "face").fit();
		m.box(1.5, 44, 4, 14.5, 46, 15.5, "wood");
		m.box(4, 46, 6, 12, 47.5, 13.5, "wood");
		m.box(7.25, 47.5, 9, 8.75, 48, 10.5, "brass");
		return m.done();
	}

	/** A grand piano, its keyboard at its front, its lid propped open over the curve of its case. */
	private static List<Box> grandPiano() {
		M m = new M();
		m.box(0, 8, 0, 32, 15, 20, "black");
		m.box(0, 8, 20, 26, 15, 34, "black");
		m.box(0, 8, 34, 18, 15, 42, "black");
		m.box(1, 8, 42, 10, 15, 45, "black");
		m.box(2, 9, -4, 30, 10.5, 0, "black");
		m.box(2.5, 10.5, -4, 29.5, 11, -0.5, "keys").top("keys").fit();
		m.box(1, 10, -4, 2, 13, 0, "black");
		m.box(30, 10, -4, 31, 13, 0, "black");
		m.box(2, 11, -0.5, 30, 13, 0.5, "black");
		for (double[] g : new double[][] {{2, 2}, {27, 2}, {12, 36}}) {
			m.box(g[0], 0, g[1], g[0] + 3, 8, g[1] + 3, "black");
			m.box(g[0] + 0.5, 0, g[1] + 0.5, g[0] + 2.5, 0.75, g[1] + 2.5, "brass");
		}
		m.box(13, 3, -1, 19, 4, 1, "brass");
		m.box(1, 15, 6, 31, 15.75, 30, "black").turn('z', 22.5, 31, 15, 18);
		m.box(14, 15, 16, 15, 25, 17, "black").turn('z', 22.5, 14.5, 15, 16.5);
		return m.done();
	}

	private static List<Box> rockingHorse() {
		M m = new M();
		m.box(2, 0, 0.5, 3, 1.5, 15.5, "wood");
		m.box(13, 0, 0.5, 14, 1.5, 15.5, "wood");
		m.box(2, 1, 1, 3, 2.5, 3, "wood");
		m.box(13, 1, 13, 14, 2.5, 15, "wood");
		legs(m, 4, 3, 12, 13, 1.5, 6, "paint");
		m.box(4, 6, 3, 12, 10, 13, "paint");
		m.box(5.5, 9, 1, 10.5, 15, 4.5, "paint");
		m.box(6, 12, -1.5, 10, 15, 2, "paint");
		m.box(7.25, 11, 3.5, 8.75, 16, 7, "mane");
		m.box(7, 6, 13, 9, 11, 14.5, "mane");
		m.box(4.5, 10, 6, 11.5, 10.5, 10, "f_leather_oxblood");
		return m.done();
	}

	/** A porcelain doll sitting propped up, in a faded dress; its head turned a little too far. */
	private static List<Box> doll() {
		M m = new M();
		m.box(5, 0, 6, 11, 4, 11, "dress");
		m.box(5.5, 0, 2.5, 7.5, 1.5, 6, "dress");
		m.box(8.5, 0, 2.5, 10.5, 1.5, 6, "dress");
		m.box(5.75, 0, 1.5, 7.25, 1.25, 2.5, "face");
		m.box(8.75, 0, 1.5, 10.25, 1.25, 2.5, "face");
		m.box(5.5, 4, 7, 10.5, 8, 10.5, "dress");
		m.box(4.5, 4.5, 7.5, 5.5, 7.5, 9, "face");
		m.box(10.5, 4.5, 7.5, 11.5, 7.5, 9, "face");
		m.box(6, 8, 6.5, 10, 12, 10.5, "face").front("face").fit().turn('y', 22.5);
		m.box(5.75, 10, 6.75, 10.25, 13, 11, "hair").front(null).turn('y', 22.5);
		return m.done();
	}

	// --- baths, kitchens, laundries, machinery ---------------------------------------------------------------

	private static void service(List<Piece> all) {
		piece(all, "bathtub", "Clawfoot Bath", Form.MULTI, "service").size(1, 1, 2).feel(Feel.STONE).model(l -> bathtub())
				.skin("enamel", "f_enamel", "outside", "f_enamel_outside");
		piece(all, "pedestal_sink", "Washbasin", Form.SINGLE, "service").feel(Feel.STONE).model(l -> washbasin())
				.skin("china", "f_porcelain");
		piece(all, "toilet", "Water Closet", Form.SINGLE, "service").feel(Feel.STONE).model(l -> toilet())
				.skin("china", "f_porcelain", "wood", "f_mahogany", "iron", "f_iron");
		piece(all, "washstand", "Washstand", Form.SINGLE, "service").model(l -> washstand())
				.skin("wood", "f_pine", "top", "marble_cream", "china", "f_porcelain");
		piece(all, "laundry_copper", "Laundry Copper", Form.SINGLE, "service").noFacing().feel(Feel.METAL).model(l -> copper())
				.skin("copper", "f_copper", "brick", "minecraft:block/bricks");
		piece(all, "mangle", "Mangle", Form.SINGLE, "service").feel(Feel.METAL).model(l -> mangle()).skin("iron", "f_iron", "wood", "f_pine");
		piece(all, "laundry_basket", "Laundry Basket", Form.SINGLE, "service").noFacing().feel(Feel.CLOTH).model(l -> basket())
				.skin("wicker", "f_wicker", "linen", "f_linen_dirty");
		piece(all, "pot_rack", "Pot Rack", Form.SINGLE, "service").mount(Mount.CEILING).feel(Feel.METAL).model(l -> potRack())
				.skin("iron", "f_iron", "copper", "f_copper");
		piece(all, "meat_hook", "Meat Hook", Form.SINGLE, "service").mount(Mount.CEILING).noFacing().feel(Feel.METAL).model(l -> meatHook())
				.skin("iron", "f_iron_rust");
		piece(all, "iron_stove", "Iron Stove", Form.SINGLE, "service").noFacing().feel(Feel.METAL).model(l -> stove()).skin("iron", "f_iron");
		piece(all, "boiler", "Boiler", Form.MULTI, "service").size(2, 2, 3).feel(Feel.METAL).model(l -> boiler())
				.skin("iron", "f_boiler", "door", "f_boiler_door");
	}

	private static List<Box> bathtub() {
		M m = new M();
		for (double x : new double[] {1, 13}) {
			for (double z : new double[] {2, 28}) {
				m.box(x, 0, z, x + 2, 2.5, z + 2, "brass");
			}
		}
		m.box(1, 2, 1.5, 15, 4, 30.5, "outside");
		m.box(0.5, 4, 1, 15.5, 10, 31, "outside").top(null);
		m.box(1.5, 4, 2, 14.5, 4.5, 30, "enamel").only(UP);
		m.box(0.5, 10, 1, 2, 11, 31, "enamel");
		m.box(14, 10, 1, 15.5, 11, 31, "enamel");
		m.box(2, 10, 1, 14, 11, 2.5, "enamel");
		m.box(2, 10, 29.5, 14, 11, 31, "enamel");
		m.box(1.5, 4.5, 2, 2, 10, 30, "enamel").only(EAST);
		m.box(14, 4.5, 2, 14.5, 10, 30, "enamel").only(WEST);
		m.box(2, 4.5, 2, 14, 10, 2.5, "enamel").only(SOUTH);
		m.box(2, 4.5, 29.5, 14, 10, 30, "enamel").only(NORTH);
		m.box(7, 11, 29.5, 9, 14, 31, "brass");
		m.box(7.5, 13, 27, 8.5, 14, 29.5, "brass");
		m.box(5, 13, 29.75, 11, 14, 30.75, "brass");
		return m.done();
	}

	private static List<Box> washbasin() {
		M m = new M();
		round(m, 8, 10, 0, 1.5, 3, "china");
		m.box(6.5, 1.5, 8.5, 9.5, 10, 11.5, "china");
		m.box(2, 10, 5, 14, 13, 15.5, "china");
		m.box(3, 12.5, 6, 13, 13.25, 13.5, "f_water");
		m.box(6.5, 13, 14, 9.5, 14, 15.5, "brass");
		m.box(4.5, 13, 14, 5.5, 15.5, 15, "brass");
		m.box(10.5, 13, 14, 11.5, 15.5, 15, "brass");
		return m.done();
	}

	private static List<Box> toilet() {
		M m = new M();
		m.box(5.5, 0, 6, 10.5, 7, 13, "china");
		round(m, 8, 7.5, 6, 9, 4, "china");
		m.box(4, 9, 3.5, 12, 10, 11.5, "wood");
		m.box(7, 9, 11.5, 9, 26, 13, "iron");
		m.box(3.5, 26, 11, 12.5, 31, 16, "iron");
		m.box(11, 18, 10.5, 11.5, 26, 11, "f_chain");
		m.box(10.5, 17, 10, 12, 18, 11.5, "f_porcelain");
		return m.done();
	}

	private static List<Box> washstand() {
		M m = new M();
		legs(m, 1.5, 4, 14.5, 15, 1.5, 12, "wood");
		m.box(1, 3, 4, 15, 4, 15, "wood");
		m.box(1, 12, 3.5, 15, 13.5, 15.5, "top");
		m.box(1, 13.5, 14, 15, 18, 15.5, "wood");
		round(m, 7, 9, 13.5, 15, 4.5, "china");
		round(m, 12, 12, 13.5, 19, 1.75, "china");
		m.box(11.5, 18, 9.5, 12.5, 19, 11, "china");
		return m.done();
	}

	private static List<Box> copper() {
		M m = new M();
		m.box(1, 0, 1, 15, 9, 15, "brick");
		round(m, 8, 8, 9, 15, 6.5, "copper");
		round(m, 8, 8, 15, 16, 7, "copper");
		m.box(6, 2, 0.5, 10, 5, 1, "f_iron");
		return m.done();
	}

	private static List<Box> mangle() {
		M m = new M();
		m.box(2, 0, 3, 3.5, 14, 13, "iron");
		m.box(12.5, 0, 3, 14, 14, 13, "iron");
		m.box(3.5, 8, 5, 12.5, 11, 8, "wood");
		m.box(3.5, 8, 8, 12.5, 11, 11, "wood");
		m.box(3, 11, 6, 13, 12, 10, "iron");
		round(m, 15, 8, 6, 14, 0.5, "iron");
		m.box(14, 5, 3, 15.5, 15, 13, "iron").turn('x', 45, 15, 10, 8);
		m.box(3.5, 2, 4, 12.5, 3, 12, "wood");
		return m.done();
	}

	private static List<Box> basket() {
		M m = new M();
		m.box(2, 0, 3, 14, 9, 13, "wicker").top(null);
		m.box(2.5, 1, 3.5, 13.5, 9, 12.5, "wicker").only(NORTH, SOUTH, EAST, WEST);
		m.box(3, 7, 4, 13, 10.5, 12, "linen");
		m.box(5, 10, 5, 10, 11.5, 9, "linen");
		return m.done();
	}

	private static List<Box> potRack() {
		M m = new M();
		m.box(0, 13, 7.5, 16, 14, 8.5, "iron");
		m.box(2, 14, 7.75, 2.5, 16, 8.25, "iron");
		m.box(13.5, 14, 7.75, 14, 16, 8.25, "iron");
		double[][] pots = {{2, 6, 4}, {7.5, 4, 3}, {12, 7, 3.5}};
		for (double[] p : pots) {
			m.box(p[0] - 0.25, p[1] + p[2] * 0.8, 7.75, p[0] + 0.25, 13, 8.25, "iron");
			round(m, p[0], 8, p[1], p[1] + p[2] * 0.8, p[2] * 0.7, "copper");
		}
		return m.done();
	}

	private static List<Box> meatHook() {
		M m = new M();
		m.box(7.5, 6, 7.5, 8.5, 16, 8.5, "iron");
		m.box(7.5, 2, 7.5, 8.5, 6, 8.5, "iron");
		m.box(7.5, 1, 8.5, 8.5, 2, 10.5, "iron");
		m.box(7.5, 2, 10, 8.5, 4, 11, "iron");
		return m.done();
	}

	private static List<Box> stove() {
		M m = new M();
		legs(m, 4, 4, 12, 12, 1.5, 2, "iron");
		round(m, 8, 8, 2, 4, 4, "iron");
		round(m, 8, 8, 4, 10, 5, "iron");
		round(m, 8, 8, 10, 12, 4, "iron");
		m.box(6.5, 12, 6.5, 9.5, 16, 9.5, "iron");
		m.box(6, 5, 2.75, 10, 8, 3.25, "f_ember");
		return m.done();
	}

	/** A riveted iron boiler lying on its side, its fire doors at the front. */
	private static List<Box> boiler() {
		M m = new M();
		m.box(1, 0, 2, 31, 4, 46, "f_brick_soot");
		m.box(2, 4, 2, 30, 28, 46, "iron");
		m.box(0, 8, 2, 32, 24, 46, "iron");
		m.box(6, 28, 4, 26, 31, 44, "iron");
		m.box(2, 4, 1, 30, 28, 2, "iron").front("door").fit();
		m.box(12, 31, 30, 20, 32, 38, "f_iron");
		return m.done();
	}

	// --- wreckage --------------------------------------------------------------------------------------------

	private static void debris(List<Piece> all) {
		piece(all, "plaster_rubble", "Fallen Plaster", Form.SINGLE, "debris").soft().feel(Feel.PLASTER).model(l -> rubble())
				.skin("rubble", "f_rubble");
		piece(all, "broken_glass", "Broken Glass", Form.SINGLE, "debris").soft().feel(Feel.GLASS).model(l -> flat("f_glass_shards"));
		piece(all, "scattered_papers", "Scattered Papers", Form.SINGLE, "debris").soft().feel(Feel.CLOTH).model(l -> papers());
		piece(all, "ash_heap", "Ash", Form.SINGLE, "debris").soft().feel(Feel.CLOTH).model(l -> ash());
		piece(all, "broken_chair", "Broken Chair", Form.SINGLE, "debris").soft().model(l -> brokenChair())
				.skin("wood", "f_mahogany", "velvet", "f_velvet_red");
		piece(all, "fallen_chandelier", "Fallen Chandelier", Form.SINGLE, "debris").soft().feel(Feel.GLASS).model(l -> fallenChandelier())
				.skin("crystal", "f_crystal");
		piece(all, "coal_heap", "Coal Heap", Form.SINGLE, "debris").soft().feel(Feel.STONE).model(l -> coal());
		piece(all, "sacks", "Sacks", Form.SINGLE, "debris").feel(Feel.CLOTH).model(l -> sacks()).skin("sack", "f_burlap");
		piece(all, "persian_rug", "Persian Rug", Form.TABLE, "rugs").noFacing().soft().feel(Feel.CLOTH)
				.model(l -> rug(l)).skin("field", "f_rug_red_field", "border", "f_rug_red");
		piece(all, "green_rug", "Green Rug", Form.TABLE, "rugs").noFacing().soft().feel(Feel.CLOTH)
				.model(l -> rug(l)).skin("field", "f_rug_green_field", "border", "f_rug_green");
		piece(all, "worn_rug", "Worn Rug", Form.TABLE, "rugs").noFacing().soft().feel(Feel.CLOTH)
				.model(l -> rug(l)).skin("field", "f_rug_worn_field", "border", "f_rug_worn");
	}

	private static List<Box> rubble() {
		M m = new M();
		m.box(2, 0, 3, 7, 1.5, 7, "rubble");
		m.box(8, 0, 2, 13, 2.5, 6, "rubble").turn('y', 22.5);
		m.box(4, 0, 9, 11, 1, 14, "rubble");
		m.box(5, 1, 10, 8, 2.75, 12.5, "rubble");
		m.box(11, 0, 9, 14, 1.75, 12, "rubble");
		m.box(0.5, 0, 12, 3, 0.75, 15, "rubble");
		return m.done();
	}

	private static List<Box> flat(String tex) {
		M m = new M();
		m.box(0, 0, 0, 16, 0.1, 16, tex).only(UP, DOWN);
		return m.done();
	}

	private static List<Box> papers() {
		M m = new M();
		m.box(2, 0, 3, 8, 0.1, 11, "f_paper").only(UP).fit().turn('y', 22.5);
		m.box(8, 0.1, 6, 14, 0.2, 14, "f_paper").only(UP).fit().turn('y', -22.5);
		m.box(5, 0.2, 1, 10, 0.3, 8, "f_paper").only(UP).fit();
		return m.done();
	}

	private static List<Box> ash() {
		M m = new M();
		m.box(1, 0, 1, 15, 0.5, 15, "f_ash");
		m.box(3, 0.5, 3, 12, 1.25, 11, "f_ash");
		m.box(5, 1.25, 5, 9, 2, 8, "f_ash");
		return m.done();
	}

	private static List<Box> brokenChair() {
		M m = new M();
		m.box(2.5, 0, 2, 13.5, 1.5, 13.5, "wood").turn('x', 22.5, 8, 0, 8);
		m.box(3, 1.5, 2.5, 13, 2.5, 12.5, "velvet").turn('x', 22.5, 8, 0, 8);
		m.box(1, 0, 10, 2.5, 1.5, 18, "wood").turn('y', 45, 1.75, 0.75, 14);
		m.box(3, 0, 13, 13, 1.5, 15, "wood");
		m.box(6, 0, 14, 9, 0.75, 16, "wood");
		m.box(12, 0, 3, 13.5, 1.5, 11, "wood").turn('y', -22.5);
		return m.done();
	}

	/** The chandelier that came down on the ballroom floor: its ring bent and tilted, crystal everywhere. */
	private static List<Box> fallenChandelier() {
		M m = new M();
		m.box(-8, 0, 7, 24, 1.5, 9, "brass").turn('z', 22.5, 8, 0, 8);
		m.box(7, 0, -8, 9, 1.5, 24, "brass");
		m.box(-6, 0, -6, 22, 1.25, -4.75, "brass");
		m.box(-6, 0, 20.75, 22, 1.25, 22, "brass");
		m.box(-6, 0, -4.75, -4.75, 1.25, 20.75, "brass").turn('y', 22.5, -5.4, 0, 8);
		m.box(20.75, 0, -4.75, 22, 1.25, 20.75, "brass");
		m.box(4, 0, 4, 12, 4, 12, "crystal").turn('x', 22.5, 8, 0, 8);
		m.box(6, 4, 6, 10, 9, 10, "brass").turn('x', 45, 8, 4, 8);
		m.box(-12, 0, -10, 28, 0.1, 26, "f_glass_shards").only(UP);
		for (double[] c : new double[][] {{-3, 2}, {18, 1}, {-1, 17}, {20, 14}, {10, -4}, {4, 21}}) {
			m.box(c[0], 0, c[1], c[0] + 1.5, 1, c[1] + 2, "crystal");
		}
		return m.done();
	}

	private static List<Box> coal() {
		M m = new M();
		m.box(0, 0, 0, 16, 2, 16, "f_coal");
		m.box(2, 2, 2, 14, 4, 14, "f_coal");
		m.box(4, 4, 4, 11, 6, 12, "f_coal");
		return m.done();
	}

	private static List<Box> sacks() {
		M m = new M();
		m.box(1, 0, 2, 8, 10, 9, "sack");
		m.box(2, 10, 3, 7, 12, 8, "sack");
		m.box(8, 0, 6, 15, 7, 14, "sack").turn('y', 22.5);
		m.box(3, 0, 9, 9, 5, 15, "sack");
		return m.done();
	}

	/**
	 * A rug, a block of it: its border down every side that doesn't join more of the same rug, the
	 * field where it does. The border texture has the border round all four of its edges, so each
	 * strip takes its own edge of it.
	 */
	private static List<Box> rug(Look l) {
		M m = new M();
		boolean n = !l.joined(0), e = !l.joined(1), s = !l.joined(2), w = !l.joined(3);
		double[] xs = {0, w ? 3 : 0, e ? 13 : 16, 16}, zs = {0, n ? 3 : 0, s ? 13 : 16, 16};
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if (xs[i + 1] <= xs[i] || zs[j + 1] <= zs[j]) {
					continue;
				}
				boolean field = i == 1 && j == 1;
				m.box(xs[i], 0, zs[j], xs[i + 1], 1, zs[j + 1], field ? "field" : "border");
			}
		}
		return m.done();
	}
}
