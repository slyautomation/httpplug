package net.runelite.client.plugins.httpplug;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XpTracker {

	private Map<Skill, ArrayList> skillXpMap = new HashMap<Skill, ArrayList>();

	private HttpServerPlugin httpPlugin;

	public XpTracker(HttpServerPlugin httpPlugin) {

		this.httpPlugin = httpPlugin;

		for (int i = 0; i < httpPlugin.skillList.length; i++) {
			ArrayList<Integer> newXpList = new ArrayList<Integer>();
			skillXpMap.put(httpPlugin.skillList[i], newXpList);
		}

	}

	public void update() {
		for (int i = 0; i < httpPlugin.skillList.length; i++) {
			Skill skillToUpdate = httpPlugin.skillList[i];
			ArrayList<Integer> xpListToUpdate = skillXpMap.get(skillToUpdate);
			int xpValueToAdd = httpPlugin.getClient().getSkillExperience(skillToUpdate);
			xpListToUpdate.add(xpValueToAdd);
		}
	}

	public int getXpData(Skill skillToGet, int tickNum) {
		ArrayList<Integer> xpListToGet = skillXpMap.get(skillToGet);
		int xpValueAtTickNum = xpListToGet.get(tickNum);
		return xpValueAtTickNum;
	}

	public int getMostRecentXp(Skill skillToGet) {
		ArrayList<Integer> xpListToGet = skillXpMap.get(skillToGet);
		return xpListToGet.get(xpListToGet.size()-1);
	}

}
