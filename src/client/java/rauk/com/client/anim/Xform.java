package rauk.com.client.anim;

import rauk.com.client.model.RaukRig.Frame;
import rauk.com.client.model.RaukRig.V;

/** A rigid transform: rotate by r, then move to o. */
public record Xform(V o, Quat r) {
	public static final Xform IDENTITY = new Xform(V.ZERO, Quat.IDENTITY);

	public static Xform of(Frame f) {
		return new Xform(f.o(), Quat.fromAxes(f.x(), f.y(), f.z()));
	}

	public V point(V p) {
		return o.add(r.rotate(p));
	}

	public V dir(V d) {
		return r.rotate(d);
	}

	/** This transform followed by a child's local one: the child's transform in this one's space. */
	public Xform then(Xform local) {
		return new Xform(point(local.o), r.mul(local.r));
	}

	/** The given transform expressed relative to this one. */
	public Xform toLocal(Xform world) {
		Quat inv = r.conj();
		return new Xform(inv.rotate(world.o.sub(o)), inv.mul(world.r));
	}

	public V toLocal(V p) {
		return r.conj().rotate(p.sub(o));
	}

	public V x() {
		return r.rotate(V.X);
	}

	public V y() {
		return r.rotate(V.Y);
	}

	public V z() {
		return r.rotate(V.Z);
	}
}
