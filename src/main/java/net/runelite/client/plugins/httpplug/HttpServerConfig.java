package net.runelite.client.plugins.httpplug;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("httpplug")
public interface HttpServerConfig extends Config
{
	@ConfigItem(keyName = "MaxObjectDistance", name = "Max Object Distance", description = "Max Distance of object to calculate within range")
	@Range(min = 0, max = 2400)
	default int reachedDistance()
	{
		return 1200;
	}
}
