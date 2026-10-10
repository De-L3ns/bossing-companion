package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/** Anchored game-message parsing: completed fight duration is distinct from PB. */
public final class CompletionMessages
{
	private static final Pattern KC = Pattern.compile("^Your (.+?) (?:kill ?count|kill count|kills count) is: ?([0-9,]+)\\.$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIME = Pattern.compile("^Fight duration: ([0-9]+:[0-9]{2}(?::[0-9]{2})?(?:\\.[0-9]+)?)(?:\\. Personal best: [0-9:.]+\\.?| \\(new personal best\\)\\.?)$", Pattern.CASE_INSENSITIVE);

	private CompletionMessages() { }
	public static KillCount killCount(String message)
	{
		Matcher match = KC.matcher(clean(message));
		if (!match.matches()) { return null; }
		Boss boss = BossCatalog.fromName(match.group(1));
		try
		{
			long count = Long.parseLong(match.group(2).replace(",", ""));
			return count > 0 ? new KillCount(boss, count) : null;
		}
		catch (NumberFormatException ignored) { return null; }
	}

	public static Duration completedTime(String message)
	{
		Matcher match = TIME.matcher(clean(message));
		if (!match.matches()) { return null; }
		try
		{
			String[] parts = match.group(1).split(":");
			BigDecimal seconds = new BigDecimal(parts[parts.length - 1]);
			int minutes = Integer.parseInt(parts[parts.length - 2]);
			if (seconds.compareTo(BigDecimal.valueOf(60)) >= 0 || parts.length == 3 && minutes >= 60) { return null; }
			seconds = seconds.add(BigDecimal.valueOf(minutes * 60L));
			if (parts.length == 3) { seconds = seconds.add(BigDecimal.valueOf(Long.parseLong(parts[0]) * 3600)); }
			long millis = seconds.movePointRight(3).longValueExact();
			return millis > 0 ? Duration.ofMillis(millis) : null;
		}
		catch (ArithmeticException | NumberFormatException ignored) { return null; }
	}

	private static String clean(String message)
	{
		return message == null ? "" : Text.removeTags(message).replace('\u00a0', ' ').trim();
	}
	@Value
	public static class KillCount { Boss boss; long count; }
}
