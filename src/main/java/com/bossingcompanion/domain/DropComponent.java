package com.bossingcompanion.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

@Value
public class DropComponent
{
	private static final Pattern FRACTION = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)/([0-9]+(?:\\.[0-9]+)?)$");
	String sourceVersion;
	String rarity;
	int rolls;
	String quantity;
	boolean approximate;
	String conditions;
	String rollUnit;
	String alternativeRarity;
	public DropComponent(String sourceVersion, String rarity, int rolls, String quantity, boolean approximate, String conditions)
	{
		this(sourceVersion, rarity, rolls, quantity, approximate, conditions, "kill", "");
	}
	public DropComponent(String sourceVersion, String rarity, int rolls, String quantity, boolean approximate, String conditions,
		String rollUnit, String alternativeRarity)
	{
		this.sourceVersion = sourceVersion; this.rarity = rarity; this.rolls = rolls; this.quantity = quantity;
		this.approximate = approximate; this.conditions = conditions; this.rollUnit = rollUnit; this.alternativeRarity = alternativeRarity;
	}

	public BigDecimal probabilityPerRoll()
	{
		if ("Always".equalsIgnoreCase(rarity)) { return BigDecimal.ONE; }
		Matcher matcher = FRACTION.matcher(rarity.replace(",", "").replace("~", "").trim());
		if (!matcher.matches()) { return null; }
		try
		{
			BigDecimal denominator = new BigDecimal(matcher.group(2));
			if (denominator.signum() <= 0) { return null; }
			BigDecimal p = new BigDecimal(matcher.group(1)).divide(denominator, MathContext.DECIMAL128);
			return p.signum() > 0 && p.compareTo(BigDecimal.ONE) <= 0 ? p : null;
		}
		catch (ArithmeticException ignored) { return null; }
	}
	public String rarityLabel()
	{
		String word = rarity.toLowerCase(Locale.ROOT).trim();
		for (String label : new String[]{"Always", "Common", "Uncommon", "Rare", "Very rare", "Varies", "Conditional", "Unknown"})
		{
			if (label.toLowerCase(Locale.ROOT).equals(word)) { return label; }
		}
		BigDecimal p = probabilityPerRoll();
		if (p == null) { return "Unknown"; }
		if (p.compareTo(BigDecimal.ONE) == 0) { return "Always"; }
		if (p.multiply(BigDecimal.valueOf(25)).compareTo(BigDecimal.ONE) >= 0) { return "Common"; }
		if (p.multiply(new BigDecimal("99.99")).compareTo(BigDecimal.ONE) >= 0) { return "Uncommon"; }
		if (p.multiply(new BigDecimal("999.99")).compareTo(BigDecimal.ONE) >= 0) { return "Rare"; }
		return "Very rare";
	}
}
