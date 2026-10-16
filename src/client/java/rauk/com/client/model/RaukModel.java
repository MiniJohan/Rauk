package rauk.com.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import rauk.com.Rauk;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.anim.RaukSkeleton;
import rauk.com.client.anim.Xform;
import rauk.com.entity.RaukEntity;

/**
 * Rauk as vanilla model parts, built from {@link RaukRig}. Model units are 1/64 block here (the
 * renderer scales the model down by 4), which gives 64 texture pixels per block. Each frame the
 * parts take their transforms from the entity's {@link RaukAnimator}.
 */
public class RaukModel extends HierarchicalModel<RaukEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Rauk.id("rauk"), "main");

	private final ModelPart root;
	private final RaukSkeleton skeleton = RaukSkeleton.shared();
	/** The model part for each skeleton joint, by index. */
	private final ModelPart[] parts;
	/** Which parts are the eyes, for the eyeshine. */
	public final boolean[] eye;

	public RaukModel(ModelPart root) {
		this.root = root;
		parts = new ModelPart[skeleton.count];
		eye = new boolean[skeleton.count];
		for (int i = 0; i < skeleton.count; i++) {
			int p = skeleton.parent[i];
			parts[i] = (p < 0 ? root : parts[p]).getChild(skeleton.names[i]);
			eye[i] = skeleton.names[i].startsWith("eye_");
		}
	}

	/** Draws only the parts that pass, for an overlay; {@link #showAll()} puts it back. */
	public void showOnly(IntPredicate keep) {
		for (int i = 0; i < parts.length; i++) {
			parts[i].skipDraw = !keep.test(i);
		}
	}

	public void showAll() {
		for (ModelPart part : parts) {
			part.skipDraw = false;
		}
	}

	public static LayerDefinition createBodyLayer() {
		RaukRig rig = RaukRig.shared();
		MeshDefinition mesh = new MeshDefinition();
		Map<RaukRig.Part, PartDefinition> defs = new HashMap<>();
		for (RaukRig.Part p : rig.parts) {
			CubeListBuilder cubes = CubeListBuilder.create();
			for (RaukRig.Box b : p.boxes) {
				double[] c = RaukRig.modelCorner(b);
				cubes.texOffs(b.u, b.v).addBox((float) c[0], (float) c[1], (float) c[2], b.w, b.h, b.d);
			}
			double[] pose = RaukRig.modelPose(p);
			PartDefinition parent = p.parent == null ? mesh.getRoot() : defs.get(p.parent);
			defs.put(p, parent.addOrReplaceChild(p.name, cubes, PartPose.offsetAndRotation((float) pose[0], (float) pose[1],
					(float) pose[2], (float) pose[3], (float) pose[4], (float) pose[5])));
		}
		return LayerDefinition.create(mesh, rig.textureWidth(), rig.textureHeight());
	}

	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(RaukEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		Xform[] local = RaukAnimator.of(entity).local;
		for (int i = 0; i < local.length; i++) {
			Xform l = local[i];
			if (l == null) {
				continue;
			}
			double[] p = RaukRig.modelPose(l.o(), l.x(), l.y(), l.z(), skeleton.parent[i] < 0);
			ModelPart part = parts[i];
			part.x = (float) p[0];
			part.y = (float) p[1];
			part.z = (float) p[2];
			part.xRot = (float) p[3];
			part.yRot = (float) p[4];
			part.zRot = (float) p[5];
		}
	}
}
