package net.runelite.client.plugins.httpplug;

import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import io.reactivex.rxjava3.annotations.Nullable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.http.api.RuneLiteAPI;
import org.pf4j.Extension;
import static java.lang.Integer.parseInt;
import static net.runelite.api.Perspective.localToCanvas;

@Extension
@PluginDescriptor(
	name = "HTTP Server Sly"
)
@Slf4j
public class HttpServerPlugin extends Plugin
{
	private static final Duration WAIT = Duration.ofSeconds(5);
	@Inject
	public Client client;
	public Skill[] skillList;
	public XpTracker xpTracker;
	public Skill mostRecentSkillGained;
	public int tickCount = 0;
	public long startTime = 0;
	public long currentTime = 0;
	public int[] xp_gained_skills;
	@Inject
	public HttpServerConfig config;
	@Inject
	public ClientThread clientThread;
	public HttpServer server;
	public int MAX_DISTANCE = 1200;
	@Provides
	private HttpServerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HttpServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		//MAX_DISTANCE = config.reachedDistance();
		skillList = Skill.values();
		xpTracker = new XpTracker(this);
		server = HttpServer.create(new InetSocketAddress(8080), 0);
		server.createContext("/stats", this::handleStats);
		server.createContext("/inv", handlerForInv(InventoryID.INVENTORY));
		server.createContext("/equip", handlerForInv(InventoryID.EQUIPMENT));
		server.createContext("/events", this::handleEvents);
		server.createContext("/npc", this::handleNPC);
		server.createContext("/objects", this::handleObjects);
		server.createContext("/doors", this::handleDoors);
		server.createContext("/post", this::handlePosts);
		server.setExecutor(Executors.newSingleThreadExecutor());
		startTime = System.currentTimeMillis();
		xp_gained_skills = new int[Skill.values().length];
		int skill_count = 0;
		server.start();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			xp_gained_skills[skill_count] = 0;
			skill_count++;
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		server.stop(1);
	}
	public Client getClient() {
		return client;
	}
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		//MAX_DISTANCE = config.reachedDistance();
		currentTime = System.currentTimeMillis();
		xpTracker.update();
		int skill_count = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			int xp_gained = handleTracker(skill);
			xp_gained_skills[skill_count] = xp_gained;
			skill_count ++;
		}
		tickCount++;
//		if (lastOpponent != null
//				&& lastTime != null
//				&& client.getLocalPlayer().getInteracting() == null)
//		{
//			if (Duration.between(lastTime, Instant.now()).compareTo(WAIT) > 0)
//			{
//				lastOpponent = null;
//			}
//		}

	}

	public int handleTracker(Skill skill){
		int startingSkillXp = xpTracker.getXpData(skill, 0);
		int endingSkillXp = xpTracker.getXpData(skill, tickCount);
		int xpGained = endingSkillXp - startingSkillXp;
		return xpGained;
	}

	public void handleStats(HttpExchange exchange) throws IOException
	{
		Player player = client.getLocalPlayer();
		JsonArray skills = new JsonArray();
		JsonObject headers = new JsonObject();
		headers.addProperty("username", client.getUsername());
		headers.addProperty("player name", player.getName());
		int skill_count = 0;
		skills.add(headers);
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			JsonObject object = new JsonObject();
			object.addProperty("stat", skill.getName());
			object.addProperty("level", client.getRealSkillLevel(skill));
			object.addProperty("boostedLevel", client.getBoostedSkillLevel(skill));
			object.addProperty("xp", client.getSkillExperience(skill));
			object.addProperty("xp gained", String.valueOf(xp_gained_skills[skill_count]));
			skills.add(object);
			skill_count++;
		}

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(skills, out);
		}
	}
	public void handleNPC(HttpExchange exchange) throws IOException
	{

		java.util.List<NPC> npcs = client.getNpcs();
		Player player = client.getLocalPlayer();

		JsonObject object = new JsonObject();
		int[] localXY = new int[]{0, 0};
		int i;
		i = 0;
		for (NPC npc : npcs)
		{

			if (npc != null)
			{
				NPCComposition composition = npc.getComposition();
				if (player.getLocalLocation().distanceTo(npc.getLocalLocation()) <= MAX_DISTANCE)
				{
					LocalPoint npcLocation = npc.getLocalLocation();
					int playerPlane = player.getWorldLocation().getPlane();
					Point npcCanvasFinal = localToCanvas(client, npcLocation, playerPlane);
					localXY = new int[]{npcCanvasFinal.getX(), npcCanvasFinal.getY()};
					if (localXY[0] > 1 && localXY[0] < MAX_DISTANCE)
					{
						if (localXY[1] > 1 && localXY[1] < MAX_DISTANCE)
						{
							String name = npc.getName() + "_" + i;
							String xy = Arrays.toString(localXY) + ", (" + npc.getWorldLocation().getX() + ", " + npc.getWorldLocation().getY() + ")";

							object.addProperty(name, xy);
						}

					}
				}
			}
			i = i +1;
		}
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}
	//---------------------------------
//	@Subscribe
//	public void onInteractingChanged(InteractingChanged event)
//	{
//		if (event.getSource() != client.getLocalPlayer())
//		{
//			return;
//		}
//
//		Actor opponent = event.getTarget();
//
//		if (opponent == null)
//		{
//			lastTime = Instant.now();
//			return;
//		}
//
//		lastOpponent = opponent;
//	}
	//---------------------------------------------

	public void handleEvents(HttpExchange exchange) throws IOException
	{
		MAX_DISTANCE = config.reachedDistance();
		Player player = client.getLocalPlayer();
		Actor npc = player.getInteracting();
		String npcName;
		int npcHealth;
		int npcHealth2;
		int health;
		int minHealth = 0;
		int maxHealth = 0;
		if (npc != null)
	{
		npcName = npc.getName();
		npcHealth = npc.getHealthScale();
		npcHealth2 = npc.getHealthRatio();
		health = 0;
		if (npcHealth2 > 0)
		{
			minHealth = 1;
			if (npcHealth > 1)
			{
				if (npcHealth2 > 1)
				{
					// This doesn't apply if healthRatio = 1, because of the special case in the server calculation that
					// health = 0 forces healthRatio = 0 instead of the expected healthRatio = 1
					minHealth = (npcHealth * (npcHealth2 - 1) + npcHealth - 2) / (npcHealth- 1);
				}
				maxHealth = (npcHealth * npcHealth2 - 1) / (npcHealth- 1);
				if (maxHealth > npcHealth)
				{
					maxHealth = npcHealth;
				}
			}
			else
			{
				// If healthScale is 1, healthRatio will always be 1 unless health = 0
				// so we know nothing about the upper limit except that it can't be higher than maxHealth
				maxHealth = npcHealth;
			}
			// Take the average of min and max possible healths
			health = (minHealth + maxHealth + 1) / 2;
		}
	}
		else
		{
			npcName = "null";
			npcHealth = 0;
			npcHealth2 = 0;
			health = 0;
	}
		JsonObject object = new JsonObject();
		JsonObject camera = new JsonObject();
		JsonObject worldPoint = new JsonObject();
		JsonObject mouse = new JsonObject();
		object.addProperty("animation", player.getAnimation());
		object.addProperty("animation pose", player.getPoseAnimation());
		object.addProperty("run energy", client.getEnergy());
		object.addProperty("game tick", client.getGameCycle());
		object.addProperty("health", client.getBoostedSkillLevel(Skill.HITPOINTS) + "/" + client.getRealSkillLevel(Skill.HITPOINTS));
		object.addProperty("interacting code", String.valueOf(player.getInteracting()));
		object.addProperty("npc name", npcName);
		object.addProperty("npc health ", minHealth);
		object.addProperty("MAX_DISTANCE", MAX_DISTANCE);
		mouse.addProperty("x", client.getMouseCanvasPosition().getX());
		mouse.addProperty("y", client.getMouseCanvasPosition().getY());
		worldPoint.addProperty("x", player.getWorldLocation().getX());
		worldPoint.addProperty("y", player.getWorldLocation().getY());
		worldPoint.addProperty("plane", player.getWorldLocation().getPlane());
		worldPoint.addProperty("regionID", player.getWorldLocation().getRegionID());
		worldPoint.addProperty("regionX", player.getWorldLocation().getRegionX());
		worldPoint.addProperty("regionY", player.getWorldLocation().getRegionY());
		camera.addProperty("yaw", client.getCameraYaw());
		camera.addProperty("pitch", client.getCameraPitch());
		camera.addProperty("x", client.getCameraX());
		camera.addProperty("y", client.getCameraY());
		camera.addProperty("z", client.getCameraZ());
		camera.addProperty("x2", client.getCameraX2());
		camera.addProperty("y2", client.getCameraY2());
		camera.addProperty("z2", client.getCameraZ2());
		object.add("worldPoint", worldPoint);
		object.add("camera", camera);
		object.add("mouse", mouse);
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}
	private HttpHandler handlerForInv(InventoryID inventoryID)
	{
		return exchange -> {
			Item[] items = invokeAndWait(() -> {
				ItemContainer itemContainer = client.getItemContainer(inventoryID);
				if (itemContainer != null)
				{
					return itemContainer.getItems();
				}
				return null;
			});

			if (items == null)
			{
				exchange.sendResponseHeaders(204, 0);
				return;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
			{
				RuneLiteAPI.GSON.toJson(items, out);
			}
		};
	}

	public void handleObjects(HttpExchange exchange) throws IOException
	{
		JsonObject obj_object = new JsonObject();
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		int z = client.getPlane();

		for (int x = 0; x < Constants.SCENE_SIZE; ++x)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];

				if (tile == null)
				{
					continue;
				}

				Player player = client.getLocalPlayer();
				if (player == null)
				{
					continue;
				}
				if (tile != null)
				{
					List<String> final_obj_name = new ArrayList<String>();
					List<String> object;
					object = renderGameObjects(tile, player, final_obj_name);
					if (object.size() > 0)
					{
					obj_object.addProperty("objects_" + x + "_" + y, String.valueOf(object));
					}
				}

			}
		}
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(obj_object, out);
		}
	}
	@SneakyThrows
	private List<String> renderGameObjects(Tile tile, Player player, List<String> obj_name)
	{
		MAX_DISTANCE = config.reachedDistance();
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			int i = 0;
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null && gameObject.getSceneMinLocation().equals(tile.getSceneLocation()))
				{
					if (player.getLocalLocation().distanceTo(gameObject.getLocalLocation()) <= MAX_DISTANCE)
					{
						LocalPoint objLocation = gameObject.getLocalLocation();
						int playerPlane = player.getWorldLocation().getPlane();
						Point objCanvasFinal = localToCanvas(client, objLocation, playerPlane);
						int name = gameObject.getId();
						String xy = "(" + gameObject.getWorldLocation().getX() + ", " + gameObject.getWorldLocation().getY() + ")";
						obj_name.add(name + ": " + xy + ", (" + objCanvasFinal.getX() + ", " + objCanvasFinal.getY() + ")");
					}
				}
				i = i +1;
			}
		}
		return obj_name;
	}

	public void handleDoors(HttpExchange exchange) throws IOException
	{
		MAX_DISTANCE = config.reachedDistance();
		JsonObject wall_object = new JsonObject();
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		int z = client.getPlane();

		for (int x = 0; x < Constants.SCENE_SIZE; ++x)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];

				if (tile == null)
				{
					continue;
				}

				Player player = client.getLocalPlayer();
				if (player == null)
				{
					continue;
				}

				TileObject tileObject = tile.getWallObject();
				if (tileObject != null)
				{
					if (player.getLocalLocation().distanceTo(tileObject.getLocalLocation()) <= MAX_DISTANCE)
					{
						ObjectComposition objectDefinition = getObjectComposition(tileObject.getId());
						LocalPoint wallLocation = tileObject.getLocalLocation();
						int playerPlane = player.getWorldLocation().getPlane();
						Point wallCanvasFinal = localToCanvas(client, wallLocation, playerPlane);
						int name = tileObject.getId();
						String xy = "(" + tileObject.getWorldLocation().getX() + ", " + tileObject.getWorldLocation().getY() + ")";
						wall_object.addProperty("walls_" + x + "_" + y, name + ", " + xy + ", (" + wallCanvasFinal.getX() + ", " + wallCanvasFinal.getY() + ")");
					}
				}

			}
		}
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(wall_object, out);
		}
	}

	private String handleGetRequest(HttpExchange httpExchange) {
		return httpExchange.getRequestURI().toString().split("\\?")[1].split("=")[1];
	}
	public void handlePosts(HttpExchange exchange) throws IOException
	{
		String requestParam = handleGetRequest(exchange);
		String[] string = requestParam.replaceAll("\\(", "")
				.replaceAll("\\)", "")
				.split(",");
		JsonObject pos_object = new JsonObject();
		Player player = client.getLocalPlayer();
		WorldPoint worldLocation = new WorldPoint(parseInt(string[0]),parseInt(string[1]),0);
		LocalPoint pos_Location = LocalPoint.fromWorld(client, worldLocation);
		int playerPlane = player.getWorldLocation().getPlane();
		Point pos_CanvasFinal = localToCanvas(client, pos_Location, playerPlane);
		pos_object.addProperty("test", requestParam + " | " + string[0] + ", (" + pos_CanvasFinal.getX() + ", " + pos_CanvasFinal.getY() + ")");
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
		{
			RuneLiteAPI.GSON.toJson(pos_object, out);
		}
	}

	@Nullable
	private ObjectComposition getObjectComposition(int id)
	{
		ObjectComposition objectComposition = client.getObjectDefinition(id);
		return objectComposition.getImpostorIds() == null ? objectComposition : objectComposition.getImpostor();
	}
	private <T> T invokeAndWait(Callable<T> r)
	{
		try
		{
			AtomicReference<T> ref = new AtomicReference<>();
			Semaphore semaphore = new Semaphore(0);
			clientThread.invokeLater(() -> {
				try
				{

					ref.set(r.call());
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
				finally
				{
					semaphore.release();
				}
			});
			semaphore.acquire();
			return ref.get();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
