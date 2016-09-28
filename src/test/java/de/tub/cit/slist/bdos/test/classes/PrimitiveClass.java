package de.tub.cit.slist.bdos.test.classes;

import java.util.Random;

public class PrimitiveClass implements java.io.Serializable {
	private static final long serialVersionUID = 1283749023269275762L;

	private boolean	bool;	// 1
	private byte	b;		// 1
	private char	c;		// 2
	private double	d;		// 8
	private float	f;		// 4
	private int		i;		// 4
	private long	l;		// 8
	private short	s;		// 2

	public PrimitiveClass() {

	}

	public PrimitiveClass(final Random r) {
		super();
		this.bool = r.nextBoolean();
		final byte[] bytes = new byte[1];
		r.nextBytes(bytes);
		this.b = bytes[0];
		this.c = (char) (r.nextInt(26) + 'a');
		this.d = r.nextDouble();
		this.f = r.nextFloat();
		this.i = r.nextInt();
		this.l = r.nextLong();
		this.s = (short) r.nextInt(Short.MAX_VALUE + 1);
	}

	public PrimitiveClass(final boolean bool, final byte b, final char c, final double d, final float f, final int i, final long l, final short s) {
		super();
		this.bool = bool;
		this.b = b;
		this.c = c;
		this.d = d;
		this.f = f;
		this.i = i;
		this.l = l;
		this.s = s;
	}

	public boolean isBool() {
		return bool;
	}

	public void setBool(final boolean bool) {
		this.bool = bool;
	}

	public byte getB() {
		return b;
	}

	public void setB(final byte b) {
		this.b = b;
	}

	public char getC() {
		return c;
	}

	public void setC(final char c) {
		this.c = c;
	}

	public double getD() {
		return d;
	}

	public void setD(final double d) {
		this.d = d;
	}

	public float getF() {
		return f;
	}

	public void setF(final float f) {
		this.f = f;
	}

	public int getI() {
		return i;
	}

	public void setI(final int i) {
		this.i = i;
	}

	public long getL() {
		return l;
	}

	public void setL(final long l) {
		this.l = l;
	}

	public short getS() {
		return s;
	}

	public void setS(final short s) {
		this.s = s;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + b;
		result = prime * result + (bool ? 1231 : 1237);
		result = prime * result + c;
		long temp;
		temp = Double.doubleToLongBits(d);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + Float.floatToIntBits(f);
		result = prime * result + i;
		result = prime * result + (int) (l ^ (l >>> 32));
		result = prime * result + s;
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final PrimitiveClass other = (PrimitiveClass) obj;
		if (b != other.b) return false;
		if (bool != other.bool) return false;
		if (c != other.c) return false;
		// if (Double.doubleToLongBits(d) != Double.doubleToLongBits(other.d)) return false;
		// if (Float.floatToIntBits(f) != Float.floatToIntBits(other.f)) return false;
		if (d != other.d) return false;
		if (f != other.f) return false;
		if (i != other.i) return false;
		if (l != other.l) return false;
		if (s != other.s) return false;
		return true;
	}

	@Override
	public String toString() {
		return "PrimitiveClass [bool=" + bool + ", b=" + b + ", c=" + c + ", d=" + d + ", f=" + f + ", i=" + i + ", l=" + l + ", s=" + s + "]";
	}

}
