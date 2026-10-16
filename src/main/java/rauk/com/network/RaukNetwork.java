package rauk.com.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.combat.Region;

/** Rauk's packets, and the interactions that matter while it holds you. */
public final class RaukNetwork {
	private RaukNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(RaukDebugPayload.TYPE, RaukDebugPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RaukHitPayload.TYPE, RaukHitPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LiftPayload.TYPE, LiftPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HotelStatePayload.TYPE, HotelStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RaukHitPayload.TYPE, (payload, context) -> hit(context.player(), payload));
	}

	/** A precise hit from the client: checked for reach, line of sight and that it is on the body. */
	private static void hit(ServerPlayer player, RaukHitPayload payload) {
		if (!(player.level().getEntity(payload.entity()) instanceof RaukEntity rauk) || !rauk.isAlive() || player.isSpectator()
				|| !player.isAlive()) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 at = payload.at();
		if (eye.distanceTo(at) > player.entityInteractionRange() + 1.5) {
			return;
		}
		Region region = Region.byId(payload.region());
		boolean onBody = rauk.getBoundingBox().inflate(1.5).contains(at) || rauk.combat().regionCenter(region).distanceTo(at) < 3.5
				|| rauk.mind().eye().distanceTo(at) < 3 || new AABB(rauk.position(), rauk.position()).inflate(6).contains(at)
						&& region == Region.TAIL;
		if (!onBody) {
			return;
		}
		var clip = player.level().clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (clip.getType() != HitResult.Type.MISS && clip.getLocation().distanceTo(eye) < eye.distanceTo(at) - 0.3) {
			return;
		}
		player.attack(rauk);
	}
}
