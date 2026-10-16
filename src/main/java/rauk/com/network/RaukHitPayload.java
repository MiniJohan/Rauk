package rauk.com.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;

/**
 * From the client: the player's attack hit Rauk here, on this region of its body. The client tests
 * the swing against Rauk's actual animated body, since its skull and limbs reach well outside its
 * collision box; the server checks the hit is plausible and applies it as a normal attack.
 */
public record RaukHitPayload(int entity, int region, Vec3 at) implements CustomPacketPayload {
	public static final Type<RaukHitPayload> TYPE = new Type<>(Rauk.id("hit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RaukHitPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.entity());
				buf.writeVarInt(p.region());
				buf.writeDouble(p.at().x);
				buf.writeDouble(p.at().y);
				buf.writeDouble(p.at().z);
			},
			buf -> new RaukHitPayload(buf.readVarInt(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
