package rauk.com.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Writes the hotel doors' assets (hotel design 4b): the panel of each kind of door, hinged left
 * and right, as models the door's renderer turns; an empty model for the block itself (it draws
 * nothing of its own); its item; its name. A panel is drawn facing north, closed along the
 * south edge of its block, two blocks high, its corridor face to the south.
 */
public final class DoorAssets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Each door: its id, its name, the texture of its edges, and its faces (the room side, the corridor side). */
	static final String[][] DOORS = {{"hotel_door", "Mahogany Door", "f_mahogany", "f_door_hotel_back", "f_door_hotel_front"},
			{"service_door", "Service Door", "f_pine", "f_door_service", "f_door_service"},
			{"glass_door", "Glazed Door", "f_mahogany", "f_door_glass", "f_door_glass"},
			{"lift_door", "Lift Gate", "brass", "f_lift_gate", "f_lift_gate"},
			{"bookcase_door", "Bookcase", "f_mahogany", "bookcase", "bookcase"}};

	private final Path assets;

	private DoorAssets(Path resources) {
		assets = resources.resolve("assets/rauk");
	}

	public static void write(Path resources) throws IOException {
		new DoorAssets(resources).writeAll();
	}

	private void writeAll() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(assets.resolve("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		for (String[] d : DOORS) {
			String id = d[0];
			for (boolean left : new boolean[] {true, false}) {
				write("models/block/" + id + "_panel_" + (left ? "left" : "right") + ".json", panel(d, left));
			}
			write("models/block/" + id + "_empty.json",
					GSON.toJson(Map.of("textures", Map.of("particle", "rauk:block/" + d[2]), "elements", new JsonArray())) + "\n");
			write("blockstates/" + id + ".json",
					GSON.toJson(Map.of("variants", Map.of("", Map.of("model", "rauk:block/" + id + "_empty")))) + "\n");
			JsonObject item = JsonParser.parseString(panel(d, true)).getAsJsonObject();
			item.add("display", JsonParser.parseString("""
					{"gui": {"rotation": [30, 225, 0], "translation": [0, -4, 0], "scale": [0.42, 0.42, 0.42]},
					 "ground": {"translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
					 "fixed": {"translation": [0, -4, 0], "scale": [0.42, 0.42, 0.42]},
					 "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
					 "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]}}"""));
			write("models/item/" + id + ".json", GSON.toJson(item) + "\n");
			lang.addProperty("block.rauk." + id, d[1]);
		}
		write("lang/en_us.json", GSON.toJson(lang) + "\n");
	}

	/** A door's panel: the slab, a brass knob and rose on each face at its free edge, two brass hinges. */
	private static String panel(String[] d, boolean left) {
		JsonArray elements = new JsonArray();
		if (d[0].equals("bookcase_door")) {
			// A section of shelving that swings (puzzle 4): no knob, no hinges to see, a block of
			// books on each face as the shelves round it have.
			for (int y : new int[] {0, 16}) {
				JsonObject half = box(0, y, 13, 16, y + 16, 16, "#edge");
				face(half, "north", "#room", new double[] {0, 0, 16, 16});
				face(half, "south", "#corridor", new double[] {0, 0, 16, 16});
				elements.add(half);
			}
			return model(d, elements);
		}
		JsonObject slab = box(0, 0, 13, 16, 32, 16, "#edge");
		face(slab, "north", "#room", new double[] {0, 0, 16, 16});
		face(slab, "south", "#corridor", new double[] {0, 0, 16, 16});
		elements.add(slab);
		double kx = left ? 13.25 : 2.75;
		for (double[] z : new double[][] {{12.5, 13}, {16, 16.5}}) {
			elements.add(box(kx - 1, 13.5, z[0], kx + 1, 16.5, z[1], "#brass"));
		}
		elements.add(box(kx - 0.75, 14.25, 11, kx + 0.75, 15.75, 12.5, "#brass"));
		elements.add(box(kx - 0.75, 14.25, 16.5, kx + 0.75, 15.75, 18, "#brass"));
		double hx = left ? -0.25 : 15.75;
		for (double y : new double[] {4, 26}) {
			elements.add(box(hx, y, 13.5, hx + 0.5, y + 2.5, 15.5, "#brass"));
		}
		return model(d, elements);
	}

	private static String model(String[] d, JsonArray elements) {
		JsonObject o = new JsonObject();
		JsonObject t = new JsonObject();
		t.addProperty("particle", "rauk:block/" + d[2]);
		t.addProperty("edge", "rauk:block/" + d[2]);
		t.addProperty("room", "rauk:block/" + d[3]);
		t.addProperty("corridor", "rauk:block/" + d[4]);
		t.addProperty("brass", "rauk:block/brass");
		o.add("textures", t);
		o.add("elements", elements);
		return GSON.toJson(o) + "\n";
	}

	private static JsonObject box(double x0, double y0, double z0, double x1, double y1, double z1, String tex) {
		JsonObject e = new JsonObject();
		e.add("from", vec(x0, y0, z0));
		e.add("to", vec(x1, y1, z1));
		JsonObject faces = new JsonObject();
		for (String f : new String[] {"north", "east", "south", "west", "up", "down"}) {
			JsonObject face = new JsonObject();
			// The edges: a strip of the tile, as tall as fits.
			face.add("uv", uv(f, x0, y0, z0, x1, y1, z1));
			face.addProperty("texture", tex);
			faces.add(f, face);
		}
		e.add("faces", faces);
		return e;
	}

	private static JsonArray uv(String f, double x0, double y0, double z0, double x1, double y1, double z1) {
		double u0, u1, v0, v1;
		switch (f) {
			case "north", "south" -> {
				u0 = x0 % 16;
				u1 = u0 + Math.min(16, x1 - x0);
				v0 = 0;
				v1 = Math.min(16, y1 - y0);
			}
			case "east", "west" -> {
				u0 = z0 % 16;
				u1 = Math.min(16, u0 + (z1 - z0));
				v0 = 0;
				v1 = Math.min(16, y1 - y0);
			}
			default -> {
				u0 = x0 % 16;
				u1 = u0 + Math.min(16, x1 - x0);
				v0 = z0 % 16;
				v1 = Math.min(16, v0 + (z1 - z0));
			}
		}
		JsonArray a = new JsonArray();
		for (double v : new double[] {Math.max(0, u0), v0, Math.min(16, u1), v1}) {
			a.add(Math.round(v * 1000) / 1000.0);
		}
		return a;
	}

	private static void face(JsonObject e, String face, String tex, double[] uv) {
		JsonObject f = e.getAsJsonObject("faces").getAsJsonObject(face);
		f.addProperty("texture", tex);
		JsonArray a = new JsonArray();
		for (double v : uv) {
			a.add(v);
		}
		f.add("uv", a);
	}

	private static JsonArray vec(double x, double y, double z) {
		JsonArray a = new JsonArray();
		a.add(x);
		a.add(y);
		a.add(z);
		return a;
	}

	private void write(String path, String text) throws IOException {
		Path p = assets.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text, StandardCharsets.UTF_8);
	}
}
