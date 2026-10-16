package rauk.com.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;

/**
 * A snapshot of Rauk's mind for the debug overlay ({@code /rauk debug}): what it believes about
 * each player, what it has heard lately, the scent trails around it, the changes it has noticed
 * in its territory, the path it is on, and what its stand-in brain is doing.
 */
public record RaukDebugPayload(int entity, Vec3 eye, String status, List<Guess> beliefs, List<Noise> noises,
		List<Scent> scent, List<Mark> anomalies, List<Vec3> path) implements CustomPacketPayload {
	public static final Type<RaukDebugPayload> TYPE = new Type<>(Rauk.id("debug"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RaukDebugPayload> CODEC = StreamCodec.of(
			(buf, p) -> p.write(buf), RaukDebugPayload::read);

	public record Guess(String name, Vec3 pos, float uncertainty, float confidence, String sense, float age, boolean seen) {
	}

	public record Noise(Vec3 pos, String label, float loudness, float age) {
	}

	public record Scent(Vec3 pos, float ageMinutes, boolean smelled) {
	}

	public record Mark(BlockPos pos, String label) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entity);
		vec(buf, eye);
		buf.writeUtf(status);
		buf.writeVarInt(beliefs.size());
		for (Guess g : beliefs) {
			buf.writeUtf(g.name());
			vec(buf, g.pos());
			buf.writeFloat(g.uncertainty());
			buf.writeFloat(g.confidence());
			buf.writeUtf(g.sense());
			buf.writeFloat(g.age());
			buf.writeBoolean(g.seen());
		}
		buf.writeVarInt(noises.size());
		for (Noise n : noises) {
			vec(buf, n.pos());
			buf.writeUtf(n.label());
			buf.writeFloat(n.loudness());
			buf.writeFloat(n.age());
		}
		buf.writeVarInt(scent.size());
		for (Scent s : scent) {
			vec(buf, s.pos());
			buf.writeFloat(s.ageMinutes());
			buf.writeBoolean(s.smelled());
		}
		buf.writeVarInt(anomalies.size());
		for (Mark m : anomalies) {
			buf.writeBlockPos(m.pos());
			buf.writeUtf(m.label());
		}
		buf.writeVarInt(path.size());
		for (Vec3 v : path) {
			vec(buf, v);
		}
	}

	private static RaukDebugPayload read(RegistryFriendlyByteBuf buf) {
		int entity = buf.readVarInt();
		Vec3 eye = vec(buf);
		String status = buf.readUtf();
		List<Guess> beliefs = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			beliefs.add(new Guess(buf.readUtf(), vec(buf), buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readFloat(),
					buf.readBoolean()));
		}
		List<Noise> noises = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			noises.add(new Noise(vec(buf), buf.readUtf(), buf.readFloat(), buf.readFloat()));
		}
		List<Scent> scent = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			scent.add(new Scent(vec(buf), buf.readFloat(), buf.readBoolean()));
		}
		List<Mark> anomalies = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			anomalies.add(new Mark(buf.readBlockPos(), buf.readUtf()));
		}
		List<Vec3> path = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			path.add(vec(buf));
		}
		return new RaukDebugPayload(entity, eye, status, beliefs, noises, scent, anomalies, path);
	}

	private static void vec(RegistryFriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 vec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}
}
