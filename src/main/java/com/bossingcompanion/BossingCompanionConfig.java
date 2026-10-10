package com.bossingcompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BossingCompanionConfig.GROUP)
public interface BossingCompanionConfig extends Config
{
	String GROUP = "bossing-companion";
	@ConfigItem(keyName = "fightTimerEnabled", name = "Enable fight timer",
		description = "Show a movable observed fight timer for Obor, Bryophyta and Vorkath. Enable before engaging; completed values remain frozen.")
	default boolean fightTimerEnabled() { return false; }
	@ConfigItem(keyName = "wikiDropRates", name = "Wiki drop rates",
		description = "Fetch public boss drop rates from the Old School RuneScape Wiki",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
	default boolean wikiDropRates() { return false; }

	@ConfigItem(keyName = "automaticRecording", name = "Automatic recording",
		description = "Start a session on the first credited boss kill when no session is active")
	default boolean automaticRecording()
	{
		return true;
	}
}
