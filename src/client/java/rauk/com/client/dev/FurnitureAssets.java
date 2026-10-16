package rauk.com.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rauk.com.client.render.GlowModels;
import rauk.com.hotel.HotelFurniture;
import rauk.com.hotel.HotelFurniture.Box;
import rauk.com.hotel.HotelFurniture.Form;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelFurniture.Look;
import rauk.com.hotel.HotelFurniture.Mount;
import rauk.com.hotel.HotelFurniture.Piece;

/**
 * Writes the furniture's assets from {@link HotelFurniture} (run with the texture painter): for
 * every state of every piece, a model of each of its cells from the piece's boxes; block states
 * picking them, turned to face the way the piece does; an item model of the whole piece, made
 * small enough to hold; and their names. Lit models are named {@code ..._lit} and mark their
 * glowing faces for {@link GlowModels}.
 */
public final class FurnitureAssets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String[] FACINGS = {"north", "east", "south", "west"};

	private final Path assets;

	private FurnitureAssets(Path resources) {
		this.assets = resources.resolve("assets/rauk");
	}

	public static void write(Path resources) throws IOException {
		new FurnitureAssets(resources).writeAll();
	}

	private void writeAll() throws IOException {
		for (Piece p : HotelFurniture.all()) {
			piece(p);
		}
		names();
	}

	/** A model's name for a state of a piece and one of its cells. */
	static String modelName(Piece p, Look l, int part) {
		StringBuilder s = new StringBuilder(p.id);
		if (p.form == Form.RUN || p.form == Form.STACK) {
			s.append('_').append(l.run());
		}
		if (p.form == Form.TABLE) {
			s.append("_j").append(l.joins());
		}
		if (p.looks > 1) {
			s.append("_l").append(l.look());
		}
		if (p.form == Form.MULTI) {
			s.append("_p").append(part);
		}
		if (l.lit()) {
			s.append(p.kind.equals("switch") ? "_on" : "_lit");
		}
		return s.toString();
	}

	private void piece(Piece p) throws IOException {
		if (p.kind.equals("switch")) {
			lightSwitch(p);
			return;
		}
		JsonObject variants = new JsonObject();
		for (Look l : p.allLooks()) {
			for (int part = 0; part < p.parts(); part++) {
				String name = modelName(p, l, part);
				write("models/block/" + name + ".json", model(p, HotelFurniture.cell(p, l, part), l.lit()));
				for (String facing : p.facing ? FACINGS : new String[] {null}) {
					variants.add(key(p, l, part, facing), variant("rauk:block/" + name, facing, p));
				}
			}
		}
		JsonObject state = new JsonObject();
		state.add("variants", variants);
		write("blockstates/" + p.id + ".json", GSON.toJson(state) + "\n");
		write("models/item/" + p.id + ".json", itemModel(p));
	}

	/** The light switch: a lever's states (face, facing, powered), the same model whatever it's on. */
	private void lightSwitch(Piece p) throws IOException {
		JsonObject variants = new JsonObject();
		for (Look l : p.allLooks()) {
			String name = modelName(p, l, 0);
			write("models/block/" + name + ".json", model(p, HotelFurniture.cell(p, l, 0), false));
			for (String face : new String[] {"floor", "wall", "ceiling"}) {
				for (String facing : FACINGS) {
					variants.add("face=" + face + ",facing=" + facing + ",powered=" + l.lit(), variant("rauk:block/" + name, facing, p));
				}
			}
		}
		JsonObject state = new JsonObject();
		state.add("variants", variants);
		write("blockstates/" + p.id + ".json", GSON.toJson(state) + "\n");
		write("models/item/" + p.id + ".json", itemModel(p));
	}

	/** A block state's key: each of its properties, by name in order. */
	private static String key(Piece p, Look l, int part, String facing) {
		Map<String, String> props = new TreeMap<>();
		if (facing != null) {
			props.put("facing", facing);
		}
		if (p.form == Form.RUN) {
			props.put("run", l.run());
		}
		if (p.form == Form.STACK) {
			props.put("stack", l.run());
		}
		if (p.form == Form.TABLE) {
			for (int i = 0; i < 4; i++) {
				props.put(FACINGS[i], Boolean.toString(l.joined(i)));
			}
		}
		if (p.form == Form.MULTI) {
			props.put("part", Integer.toString(part));
		}
		if (p.looks > 1) {
			props.put("look", Integer.toString(l.look()));
		}
		if (p.light != Light.NONE) {
			props.put("lit", Boolean.toString(l.lit()));
		}
		StringBuilder s = new StringBuilder();
		props.forEach((k, v) -> s.append(s.isEmpty() ? "" : ",").append(k).append('=').append(v));
		return s.toString();
	}

	private static JsonObject variant(String model, String facing, Piece p) {
		JsonObject v = new JsonObject();
		v.addProperty("model", model);
		int y = facing == null ? 0 : switch (facing) {
			case "east" -> 90;
			case "south" -> 180;
			case "west" -> 270;
			default -> 0;
		};
		if (y != 0) {
			v.addProperty("y", y);
		}
		return v;
	}

	/** A block model of boxes, each face textured by its role, glowing faces marked when lit. */
	private static String model(Piece p, List<Box> boxes, boolean lit) {
		Map<String, String> textures = new LinkedHashMap<>();
		JsonArray elements = new JsonArray();
		for (Box b : boxes) {
			elements.add(element(p, b, 1, new double[3], lit, textures));
		}
		return json(p, textures, elements, null);
	}

	private static String json(Piece p, Map<String, String> textures, JsonArray elements, String parent) {
		JsonObject o = new JsonObject();
		if (parent != null) {
			o.addProperty("parent", parent);
		}
		if (p.mount != Mount.FLOOR || p.light != Light.NONE) {
			o.addProperty("ambientocclusion", false);
		}
		JsonObject t = new JsonObject();
		t.addProperty("particle", textures.isEmpty() ? p.texture(p.skin.isEmpty() ? "f_mahogany" : p.skin.keySet().iterator().next())
				: textures.keySet().iterator().next());
		textures.forEach((tex, key) -> t.addProperty(key, tex));
		o.add("textures", t);
		o.add("elements", elements);
		return GSON.toJson(o) + "\n";
	}

	/**
	 * One element. Its texture coordinates come from where the box is in its piece; its corners
	 * may be moved and scaled (for the item model) about a centre.
	 */
	private static JsonObject element(Piece p, Box b, double scale, double[] shift, boolean lit,
			Map<String, String> textures) {
		JsonObject e = new JsonObject();
		e.add("from", vec(b.x0() * scale + shift[0], b.y0() * scale + shift[1], b.z0() * scale + shift[2]));
		e.add("to", vec(b.x1() * scale + shift[0], b.y1() * scale + shift[1], b.z1() * scale + shift[2]));
		if (b.turned()) {
			JsonObject r = new JsonObject();
			r.add("origin", vec(b.ox() * scale + shift[0], b.oy() * scale + shift[1], b.oz() * scale + shift[2]));
			r.addProperty("axis", String.valueOf(b.axis()));
			r.addProperty("angle", b.angle());
			e.add("rotation", r);
		}
		boolean glow = lit && b.glow();
		if (glow) {
			e.addProperty("shade", false);
		}
		JsonObject faces = new JsonObject();
		for (int f = 0; f < 6; f++) {
			String role = b.faces()[f];
			if (role == null) {
				continue;
			}
			String tex = p.texture(role) + (glow ? "_lit" : "");
			String key = textures.computeIfAbsent(tex, k -> "t" + textures.size());
			JsonObject face = new JsonObject();
			double[] uv = HotelFurniture.uv(b, f);
			JsonArray a = new JsonArray();
			for (double v : uv) {
				a.add(v);
			}
			face.add("uv", a);
			face.addProperty("texture", "#" + key);
			if (glow) {
				face.addProperty("tintindex", GlowModels.GLOW);
			}
			faces.add(HotelFurniture.FACES[f], face);
		}
		e.add("faces", faces);
		return e;
	}

	private static JsonArray vec(double x, double y, double z) {
		JsonArray a = new JsonArray();
		a.add(Math.round(x * 1000) / 1000.0);
		a.add(Math.round(y * 1000) / 1000.0);
		a.add(Math.round(z * 1000) / 1000.0);
		return a;
	}

	/**
	 * The item: the whole piece, unlit, joined to nothing; one that's bigger than a block is shrunk
	 * to fit one and centred.
	 */
	private static String itemModel(Piece p) {
		Look l = p.allLooks().get(0);
		List<Box> boxes = new ArrayList<>();
		for (int part = 0; part < p.parts(); part++) {
			int[] c = p.cellOf(part);
			for (Box b : HotelFurniture.cell(p, l, part)) {
				boxes.add(moved(b, c[0] * 16, c[1] * 16, c[2] * 16));
			}
		}
		double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (Box b : boxes) {
			double[] a = {b.x0(), b.y0(), b.z0()}, z = {b.x1(), b.y1(), b.z1()};
			for (int i = 0; i < 3; i++) {
				lo[i] = Math.min(lo[i], a[i]);
				hi[i] = Math.max(hi[i], z[i]);
			}
		}
		double extent = 0;
		for (int i = 0; i < 3; i++) {
			extent = Math.max(extent, hi[i] - lo[i]);
		}
		boolean fits = lo[0] >= 0 && lo[1] >= 0 && lo[2] >= 0 && hi[0] <= 16 && hi[1] <= 16 && hi[2] <= 16;
		double scale = fits ? 1 : 16 / extent;
		double[] shift = new double[3];
		if (!fits) {
			for (int i = 0; i < 3; i++) {
				shift[i] = 8 - (lo[i] + hi[i]) / 2 * scale;
			}
		}
		Map<String, String> textures = new LinkedHashMap<>();
		JsonArray elements = new JsonArray();
		for (Box b : boxes) {
			// Turned boxes can't be shrunk past their limits cleanly; they're small, so they stay.
			elements.add(element(p, b, scale, shift, false, textures));
		}
		return json(p, textures, elements, "minecraft:block/block");
	}

	private static Box moved(Box b, double dx, double dy, double dz) {
		double[] fit = b.fit() == null ? null
				: new double[] {b.fit()[0] + dx, b.fit()[1] + dy, b.fit()[2] + dz, b.fit()[3] + dx, b.fit()[4] + dy, b.fit()[5] + dz};
		return new Box(b.x0() + dx, b.y0() + dy, b.z0() + dz, b.x1() + dx, b.y1() + dy, b.z1() + dz, b.faces(), b.glow(), b.axis(), b.angle(),
				b.ox() + dx, b.oy() + dy, b.oz() + dz, fit);
	}

	private void write(String path, String text) throws IOException {
		Path p = assets.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text, StandardCharsets.UTF_8);
	}

	private void names() throws IOException {
		Path p = assets.resolve("lang/en_us.json");
		JsonObject lang = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
		lang.addProperty("itemGroup.rauk.furniture", "The Raukvik Grand: Furnishings");
		for (Piece piece : HotelFurniture.all()) {
			lang.addProperty("block.rauk." + piece.id, piece.name);
		}
		Files.writeString(p, GSON.toJson(lang) + "\n", StandardCharsets.UTF_8);
	}
}
