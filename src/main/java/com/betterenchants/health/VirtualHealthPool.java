package com.betterenchants.health;

import java.math.BigDecimal;
import net.minecraft.nbt.NbtCompound;

/** 单个实体的 BigDecimal 血池状态。 */
public final class VirtualHealthPool {
	private boolean enabled;
	private BigDecimal current = VirtualNumbers.ZERO;
	private BigDecimal max = VirtualNumbers.ZERO;
	/** 启用前的属性 base（壳化前），用于天启乘区重算真实 max。 */
	private double designBase = 20.0;
	/** 假人等：无限血不扣 current。 */
	private boolean infinite;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isInfinite() {
		return infinite;
	}

	public void setInfinite(boolean infinite) {
		this.infinite = infinite;
	}

	public double getDesignBase() {
		return designBase;
	}

	public void setDesignBase(double designBase) {
		this.designBase = designBase > 0.0 && Double.isFinite(designBase) ? designBase : 20.0;
	}

	public BigDecimal getCurrent() {
		return current;
	}

	public BigDecimal getMax() {
		return max;
	}

	public void setCurrent(BigDecimal value) {
		this.current = value == null ? VirtualNumbers.ZERO : VirtualNumbers.max(VirtualNumbers.ZERO, value);
		if (!infinite && this.max.signum() > 0 && this.current.compareTo(this.max) > 0) {
			this.current = this.max;
		}
	}

	public void setMax(BigDecimal newMax) {
		setMax(newMax, true);
	}

	/** @param scaleCurrent 是否按旧比例缩放当前血 */
	public void setMax(BigDecimal newMax, boolean scaleCurrent) {
		BigDecimal nm = newMax == null || newMax.signum() <= 0 ? VirtualNumbers.ONE : newMax;
		if (scaleCurrent && this.max.signum() > 0 && !infinite) {
			BigDecimal ratio = this.current.divide(this.max, VirtualNumbers.MATH);
			this.max = nm;
			this.current = this.max.multiply(ratio, VirtualNumbers.MATH);
		} else {
			this.max = nm;
			if (!infinite && this.current.compareTo(this.max) > 0) {
				this.current = this.max;
			}
			// current==0 时不要回满，否则 /kill 与致死会被抬血
		}
	}

	public void fill() {
		if (!infinite) {
			this.current = this.max;
		}
	}

	/** @return 实际扣除量 */
	public BigDecimal applyDamage(BigDecimal amount) {
		if (!enabled || infinite || amount == null || amount.signum() <= 0) {
			return VirtualNumbers.ZERO;
		}
		BigDecimal take = VirtualNumbers.min(this.current, amount);
		this.current = this.current.subtract(take);
		if (this.current.signum() < 0) {
			this.current = VirtualNumbers.ZERO;
		}
		return take;
	}

	/** @return 实际回复量 */
	public BigDecimal heal(BigDecimal amount) {
		if (!enabled || infinite || amount == null || amount.signum() <= 0) {
			return VirtualNumbers.ZERO;
		}
		BigDecimal before = this.current;
		this.current = VirtualNumbers.min(this.max, this.current.add(amount));
		return this.current.subtract(before);
	}

	public boolean isDead() {
		return enabled && !infinite && this.current.signum() <= 0;
	}

	/** 血量比例 0..1。 */
	public float ratio() {
		if (infinite) {
			return 1.0f;
		}
		if (max.signum() <= 0) {
			return 0.0f;
		}
		double r = current.divide(max, VirtualNumbers.MATH).doubleValue();
		if (Double.isNaN(r) || r < 0.0) {
			return 0.0f;
		}
		if (r > 1.0) {
			return 1.0f;
		}
		return (float) r;
	}

	public void writeNbt(NbtCompound nbt) {
		nbt.putBoolean("be_virtual_on", enabled);
		nbt.putBoolean("be_virtual_inf", infinite);
		nbt.putDouble("be_virtual_base", designBase);
		nbt.putString("be_virtual_hp", VirtualNumbers.format(current));
		nbt.putString("be_virtual_max", VirtualNumbers.format(max));
	}

	public void readNbt(NbtCompound nbt) {
		enabled = nbt.getBoolean("be_virtual_on");
		infinite = nbt.getBoolean("be_virtual_inf");
		if (nbt.contains("be_virtual_base")) {
			designBase = nbt.getDouble("be_virtual_base");
		}
		max = VirtualNumbers.parse(nbt.getString("be_virtual_max"), VirtualNumbers.ONE);
		current = VirtualNumbers.parse(nbt.getString("be_virtual_hp"), max);
	}

	public void enableWith(BigDecimal maxHp, BigDecimal currentHp) {
		this.enabled = true;
		this.max = maxHp == null || maxHp.signum() <= 0 ? VirtualNumbers.ONE : maxHp;
		this.current = currentHp == null ? this.max : VirtualNumbers.min(this.max, VirtualNumbers.max(VirtualNumbers.ZERO, currentHp));
	}
}
