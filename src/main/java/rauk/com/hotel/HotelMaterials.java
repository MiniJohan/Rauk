package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The hotel's own blocks (hotel design 11.3), as one list both the game and the asset tools read:
 * the game registers a block for each, the texture painter paints its textures, and the asset
 * writer writes its block states, models, item models and names. Plain Java, no game classes.
 */
public final class HotelMaterials {
	/** How a block is shaped, which decides its class, its model and its block states. */
	public enum Shape {
		/** A full block, the same all round. */
		CUBE,
		/** A full block with a carved front (a keystone). */
		FACING,
		/** A square column, in base, shaft and capital pieces stacked. */
		COLUMN,
		/** A flat column against a wall, in base, shaft and capital pieces. */
		PILASTER,
		/** A moulded cornice standing out from a wall, overhanging at the top. */
		CORNICE,
		/** A run of stone balusters with a rail, along x or z. */
		BALUSTRADE,
		/** Ornate iron, joining up like iron bars. */
		RAILING,
		/** A carpet runner: its middle, or an edge with the gold border. */
		RUNNER,
		STAIRS,
		SLAB,
		/** Marble stairs with a runner up them. */
		CARPET_STAIRS,
		/** A pipe along x, y or z. */
		PIPE,
		/** A brass finial on a roof. */
		FINIAL,
		/**
		 * A face at 45 degrees across the block, the half behind it filled (hotel design 14): set in
		 * the steps of a slanted wall, a row of them makes it a flat wall. {@code other} is the
		 * texture it shows (a vanilla one with its namespace).
		 */
		SLANT,
		/** A brass letter standing on its edge, for the hotel's name over the corner. */
		LETTER,
		/** A wire of bulbs, along x or z, lit or (mostly) dead. */
		FESTOON,
		/** A slender metal post in base, shaft and capital pieces, the capital branching out to hold a canopy. */
		POST
	}

	/** What it is made of: its sound and how hard it is. */
	public enum Feel { WOOD, STONE, METAL, CLOTH, TILE, PLASTER, GLASS }

	/**
	 * One of the hotel's blocks. Its textures are named after it ({@code id}, or {@code id_base},
	 * {@code id_shaft} and {@code id_capital} for columns, and so on); {@code other} is the texture
	 * for its plain faces, or the base block of stairs and slabs.
	 */
	public record Material(String id, String name, Shape shape, Feel feel, boolean climbable, String other, int variants) {
		public Material(String id, String name, Shape shape, Feel feel, boolean climbable, String other) {
			this(id, name, shape, feel, climbable, other, 1);
		}

		/** The same with several looks, picked at random block by block. */
		public Material looks(int n) {
			return new Material(id, name, shape, feel, climbable, other, n);
		}
	}

	public static final List<Material> ALL;

	/**
	 * The surfaces of slanted walls: inside, every style's panelling and walls; outside, the stone,
	 * the slate and the shutters. Texture (vanilla ones with their namespace), name, feel, climbable.
	 */
	private static final String[][] SLANTS = {
			{"mahogany_wainscot", "Mahogany Wainscot", "WOOD", "yes"}, {"mahogany_wainscot_worn", "Worn Mahogany Wainscot", "WOOD", "yes"},
			{"charred_wainscot", "Charred Wainscot", "WOOD", "yes"}, {"damask_green", "Green Damask", "PLASTER", "yes"},
			{"damask_green_worn", "Faded Green Damask", "PLASTER", "yes"}, {"damask_red", "Red Damask", "PLASTER", "yes"},
			{"damask_red_worn", "Faded Red Damask", "PLASTER", "yes"}, {"cream_plaster_worn", "Faded Cream Plaster", "PLASTER", "yes"},
			{"ballroom_panel_worn", "Peeling Gilded Panel", "PLASTER", "yes"}, {"marble_wall", "Marble Wall", "STONE", "no"},
			{"marble_black", "Black Marble", "STONE", "no"}, {"tile_wall_grimy", "Grimy Tile", "TILE", "no"},
			{"plaster_peeling", "Peeling Plaster", "PLASTER", "yes"}, {"concrete_stained", "Stained Concrete", "STONE", "yes"},
			{"algae_mosaic", "Algae Mosaic", "TILE", "no"}, {"minecraft:block/bricks", "Brick", "STONE", "yes"},
			{"minecraft:block/mossy_stone_bricks", "Mossy Stone Brick", "STONE", "yes"},
			{"minecraft:block/stone_bricks", "Stone Brick", "STONE", "yes"}, {"ashlar", "Cream Ashlar", "STONE", "yes"},
			{"ashlar_stained", "Weathered Ashlar", "STONE", "yes"}, {"limestone", "Limestone", "STONE", "yes"},
			{"rusticated_limestone", "Rusticated Limestone", "STONE", "yes"}, {"slate_shingles", "Slate", "STONE", "yes"},
			{"shutter", "Shutter", "METAL", "no"},
			// What a slanted window is once its shutter has risen (the finale).
			{"minecraft:block/glass", "Glass", "GLASS", "no"}};

	/** The slanted piece showing a texture: {@code slant_} and the texture's own name. */
	public static String slantId(String texture) {
		return "slant_" + texture.substring(texture.lastIndexOf('/') + 1);
	}

	static {
		List<Material> m = new ArrayList<>();
		// Outside.
		m.add(new Material("stucco", "Blue Stucco", Shape.CUBE, Feel.STONE, true, null).looks(2));
		m.add(new Material("stucco_stained", "Stained Blue Stucco", Shape.CUBE, Feel.STONE, true, null).looks(3));
		m.add(new Material("limestone", "Limestone", Shape.CUBE, Feel.STONE, true, null).looks(2));
		m.add(new Material("rusticated_limestone", "Rusticated Limestone", Shape.CUBE, Feel.STONE, true, null));
		m.add(new Material("limestone_keystone", "Limestone Keystone", Shape.FACING, Feel.STONE, true, "limestone"));
		m.add(new Material("limestone_cornice", "Limestone Cornice", Shape.CORNICE, Feel.STONE, true, "limestone"));
		m.add(new Material("limestone_pilaster", "Limestone Pilaster", Shape.PILASTER, Feel.STONE, true, "limestone"));
		m.add(new Material("limestone_column", "Limestone Column", Shape.COLUMN, Feel.STONE, true, "limestone"));
		m.add(new Material("limestone_balustrade", "Limestone Balustrade", Shape.BALUSTRADE, Feel.STONE, false, "limestone"));
		m.add(new Material("slate_shingles", "Slate Shingles", Shape.CUBE, Feel.STONE, true, null).looks(2));
		m.add(new Material("slate_shingle_stairs", "Slate Shingle Stairs", Shape.STAIRS, Feel.STONE, true, "slate_shingles"));
		m.add(new Material("slate_shingle_slab", "Slate Shingle Slab", Shape.SLAB, Feel.STONE, true, "slate_shingles"));
		m.add(new Material("wrought_iron_railing", "Wrought Iron Railing", Shape.RAILING, Feel.METAL, false, null));
		m.add(new Material("brass_finial", "Brass Finial", Shape.FINIAL, Feel.METAL, false, "brass"));
		// Walls inside.
		m.add(new Material("mahogany_wainscot", "Mahogany Wainscot", Shape.CUBE, Feel.WOOD, true, null));
		m.add(new Material("mahogany_wainscot_worn", "Worn Mahogany Wainscot", Shape.CUBE, Feel.WOOD, true, null).looks(3));
		m.add(new Material("charred_wainscot", "Charred Wainscot", Shape.CUBE, Feel.WOOD, true, null).looks(2));
		m.add(new Material("damask_green", "Green Damask", Shape.CUBE, Feel.PLASTER, true, null));
		m.add(new Material("damask_green_worn", "Faded Green Damask", Shape.CUBE, Feel.PLASTER, true, null).looks(3));
		m.add(new Material("damask_red", "Red Damask", Shape.CUBE, Feel.PLASTER, true, null));
		m.add(new Material("damask_red_worn", "Faded Red Damask", Shape.CUBE, Feel.PLASTER, true, null).looks(3));
		m.add(new Material("ballroom_panel", "Gilded Panel", Shape.CUBE, Feel.PLASTER, true, null));
		m.add(new Material("ballroom_panel_worn", "Peeling Gilded Panel", Shape.CUBE, Feel.PLASTER, true, null).looks(3));
		m.add(new Material("cream_plaster_worn", "Faded Cream Plaster", Shape.CUBE, Feel.PLASTER, true, null).looks(3));
		m.add(new Material("marble_wall", "Marble Wall", Shape.CUBE, Feel.STONE, false, null).looks(3));
		m.add(new Material("tile_wall_grimy", "Grimy Tiles", Shape.CUBE, Feel.TILE, false, null).looks(3));
		m.add(new Material("plaster_peeling", "Peeling Plaster", Shape.CUBE, Feel.PLASTER, true, null).looks(10));
		m.add(new Material("concrete_stained", "Stained Concrete", Shape.CUBE, Feel.STONE, true, null).looks(3));
		// Floors.
		m.add(new Material("dark_boards", "Dark Boards", Shape.CUBE, Feel.WOOD, true, null).looks(3));
		m.add(new Material("parquet", "Herringbone Parquet", Shape.CUBE, Feel.WOOD, true, null));
		m.add(new Material("marble_cream", "Cream Marble", Shape.CUBE, Feel.STONE, false, null).looks(4));
		m.add(new Material("marble_black", "Black Marble", Shape.CUBE, Feel.STONE, false, null).looks(4));
		m.add(new Material("tile_floor_grimy", "Grimy Floor Tiles", Shape.CUBE, Feel.TILE, false, null).looks(3));
		m.add(new Material("charred_boards", "Charred Boards", Shape.CUBE, Feel.WOOD, true, null).looks(2));
		m.add(new Material("algae_mosaic", "Algae Mosaic", Shape.CUBE, Feel.TILE, false, null).looks(3));
		m.add(new Material("runner", "Runner", Shape.RUNNER, Feel.CLOTH, false, null));
		m.add(new Material("carpeted_marble_stairs", "Carpeted Marble Stairs", Shape.CARPET_STAIRS, Feel.STONE, false, "marble_cream"));
		m.add(new Material("marble_stairs", "Cream Marble Stairs", Shape.STAIRS, Feel.STONE, false, "marble_cream"));
		// Ceilings.
		m.add(new Material("coffered_mahogany", "Coffered Mahogany", Shape.CUBE, Feel.WOOD, true, null));
		m.add(new Material("plaster_ceiling", "Plaster Ceiling", Shape.CUBE, Feel.PLASTER, true, null).looks(2));
		m.add(new Material("plaster_ceiling_cracked", "Cracked Plaster Ceiling", Shape.CUBE, Feel.PLASTER, true, null).looks(4));
		m.add(new Material("gilded_ceiling", "Gilded Ceiling", Shape.CUBE, Feel.PLASTER, true, null));
		// Structure and metal.
		m.add(new Material("mahogany_column", "Mahogany Column", Shape.COLUMN, Feel.WOOD, true, "dark_boards"));
		m.add(new Material("mahogany_pilaster", "Mahogany Pilaster", Shape.PILASTER, Feel.WOOD, true, "dark_boards"));
		m.add(new Material("marble_column", "Marble Column", Shape.COLUMN, Feel.STONE, true, "marble_cream"));
		m.add(new Material("iron_lattice", "Iron Lattice", Shape.RAILING, Feel.METAL, false, null));
		m.add(new Material("brass", "Tarnished Brass", Shape.CUBE, Feel.METAL, false, null).looks(2));
		m.add(new Material("iron_pipe", "Iron Pipe", Shape.PIPE, Feel.METAL, true, null));
		// Fittings that fill a block (hotel design 12): shelves and cases against the walls.
		m.add(new Material("bookcase", "Mahogany Bookcase", Shape.FACING, Feel.WOOD, true, "f_mahogany").looks(3));
		m.add(new Material("bookcase_worn", "Dusty Bookcase", Shape.FACING, Feel.WOOD, true, "f_mahogany_worn").looks(2));
		m.add(new Material("pigeonholes", "Key Pigeonholes", Shape.FACING, Feel.WOOD, true, "f_mahogany"));
		m.add(new Material("wine_rack", "Wine Rack", Shape.FACING, Feel.WOOD, true, "f_oak").looks(2));
		m.add(new Material("pantry_shelves", "Pantry Shelves", Shape.FACING, Feel.WOOD, true, "f_pine").looks(2));
		m.add(new Material("linen_shelves", "Linen Shelves", Shape.FACING, Feel.WOOD, true, "f_pine"));
		m.add(new Material("bottle_shelves", "Bar Shelves", Shape.FACING, Feel.WOOD, true, "f_mahogany").looks(2));
		m.add(new Material("bar_cabinet", "Bar Cabinet", Shape.FACING, Feel.WOOD, true, "f_mahogany"));
		m.add(new Material("crate", "Crate", Shape.CUBE, Feel.WOOD, true, null).looks(2));
		// The lift cars (hotel design 3.2).
		m.add(new Material("lift_floor", "Lift Floor", Shape.CUBE, Feel.METAL, false, null));
		m.add(new Material("lift_roof", "Lift Car Roof", Shape.CUBE, Feel.METAL, true, null));
		// The reshaped hotel (hotel design 14): cream stone all round, the corner's ironwork and name.
		m.add(new Material("ashlar", "Cream Ashlar", Shape.CUBE, Feel.STONE, true, null).looks(2));
		m.add(new Material("ashlar_stained", "Weathered Ashlar", Shape.CUBE, Feel.STONE, true, null).looks(3));
		m.add(new Material("brass_lattice", "Brass Lattice", Shape.RAILING, Feel.METAL, false, null));
		m.add(new Material("brass_column", "Brass Post", Shape.POST, Feel.METAL, false, "brass"));
		m.add(new Material("canopy_bronze", "Bronze Canopy Plate", Shape.CUBE, Feel.METAL, false, null));
		m.add(new Material("canopy_slab", "Bronze Canopy", Shape.SLAB, Feel.METAL, false, "canopy_bronze"));
		m.add(new Material("brass_letter", "Brass Letter", Shape.LETTER, Feel.METAL, false, "brass"));
		m.add(new Material("festoon", "String of Bulbs", Shape.FESTOON, Feel.GLASS, false, "f_bulb"));
		m.add(new Material("dead_hedge", "Dead Hedge", Shape.CUBE, Feel.WOOD, true, null).looks(2));
		m.add(new Material("cinema_screen", "Cinema Screen", Shape.CUBE, Feel.CLOTH, false, null).looks(3));
		m.add(new Material("carpet_floor", "Fitted Carpet", Shape.CUBE, Feel.CLOTH, false, null));
		// The slanted pieces of the 45-degree walls, one for each surface such a wall has.
		for (String[] sl : SLANTS) {
			m.add(new Material(slantId(sl[0]), sl[1] + " Slant", Shape.SLANT, Feel.valueOf(sl[2]), !sl[3].equals("no"), sl[0]));
		}
		ALL = Collections.unmodifiableList(m);
	}

	private HotelMaterials() {
	}

	public static Material get(String id) {
		for (Material m : ALL) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		throw new IllegalArgumentException("No hotel material " + id);
	}

	/** The names of a texture's looks: the texture itself, then texture_1, texture_2... */
	public static List<String> looks(String texture, int n) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(i == 0 ? texture : texture + "_" + i);
		}
		return out;
	}

	/** The textures a material needs painted (its first look; the others are named after it). */
	public static List<String> textures(Material m) {
		return switch (m.shape()) {
			case COLUMN, PILASTER -> List.of(m.id() + "_base", m.id() + "_shaft", m.id() + "_capital");
			case RUNNER -> List.of("runner_center", "runner_edge");
			case RAILING -> List.of(m.id(), m.id() + "_edge");
			case STAIRS, SLAB, CARPET_STAIRS, FINIAL, BALUSTRADE, SLANT, LETTER, FESTOON, POST -> List.of();
			case CORNICE -> List.of(m.id());
			default -> List.of(m.id());
		};
	}
}
