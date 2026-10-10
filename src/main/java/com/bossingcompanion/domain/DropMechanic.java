package com.bossingcompanion.domain;

import lombok.Value;

@Value
public class DropMechanic
{
	public enum Kind { GUARANTEED_MILESTONE, MULTIPLE_COMPONENTS, CONDITIONAL, AVERAGE_ONLY, UNMODELED }
	Kind kind;
	String description;
	String source;
	String reviewedAt;
	Long guaranteedKill;
	boolean replacesRandomRoll;
}
