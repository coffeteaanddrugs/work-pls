package com.smartphone.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartphone.PhoneMod;
import com.smartphone.common.Contact;
import com.smartphone.common.Json;
import com.smartphone.common.Message;
import com.smartphone.common.Numbers;
import com.smartphone.net.PhoneS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Toda la lógica del lado del servidor (mensajes, contactos, sincronización). */
public final class ServerHandler {
	public static PhoneData data;
	public static final CallManager calls = new CallManager();

	private static final int MAX_TEXT = 256;
	private static final int MAX_SYNC_MESSAGES = 300;
	private static int saveTimer;

	private ServerHandler() {
	}

	// ---------- ciclo de vida ----------

	public static void onServerStarting(MinecraftServer server) {
		data = PhoneData.load(server.getSavePath(WorldSavePath.ROOT).resolve("smartphone.json"));
		PhoneMod.LOGGER.info("Smartphone: datos cargados ({} números)", data.numbers.size());
	}

	public static void onServerStopping(MinecraftServer server) {
		calls.endAll(server);
		if (data != null) {
			data.save();
		}
	}

	public static void onTick(MinecraftServer server) {
		if (data == null) {
			return;
		}
		calls.tick(server);
		if (++saveTimer >= 600) {
			saveTimer = 0;
			if (data.isDirty()) {
				data.save();
			}
		}
	}

	// ---------- envío ----------

	public static void send(ServerPlayerEntity player, String action, JsonElement body) {
		ServerPlayNetworking.send(player, new PhoneS2C(action, Json.GSON.toJson(body)));
	}

	public static void toast(ServerPlayerEntity player, String text) {
		JsonObject o = new JsonObject();
		o.addProperty("text", text);
		send(player, "toast", o);
	}

	public static ServerPlayerEntity online(MinecraftServer server, String uuid) {
		if (uuid == null) {
			return null;
		}
		try {
			return server.getPlayerManager().getPlayer(UUID.fromString(uuid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void sendMessages(ServerPlayerEntity to, String peer, List<Message> messages, boolean notify, boolean reset) {
		JsonObject o = new JsonObject();
		o.addProperty("peer", peer);
		o.addProperty("notify", notify);
		o.addProperty("reset", reset);
		o.add("messages", Json.GSON.toJsonTree(messages));
		send(to, "msgs", o);
	}

	public static void sendContacts(ServerPlayerEntity p) {
		JsonObject o = new JsonObject();
		o.add("contacts", Json.GSON.toJsonTree(data.contactsOf(p.getUuid().toString())));
		send(p, "contacts", o);
	}

	public static void sendCallLog(ServerPlayerEntity p) {
		JsonObject o = new JsonObject();
		o.add("calls", Json.GSON.toJsonTree(data.logOf(p.getUuid().toString())));
		send(p, "calllog", o);
	}

	// ---------- conexión ----------

	public static void onJoin(ServerPlayerEntity player, MinecraftServer server) {
		if (data == null) {
			return;
		}
		String uuid = player.getUuid().toString();
		String number = data.numberFor(player.getUuid());

		JsonObject init = new JsonObject();
		init.addProperty("number", number);
		init.add("contacts", Json.GSON.toJsonTree(data.contactsOf(uuid)));
		init.add("calls", Json.GSON.toJsonTree(data.logOf(uuid)));
		send(player, "init", init);

		// Mensajes que llegaron mientras estabas desconectado: pasan a "entregado"
		int unread = 0;
		for (String peer : data.peersOf(number)) {
			List<Message> conv = data.conversation(number, peer);
			List<Message> changed = new ArrayList<>();
			for (Message m : conv) {
				if (m.to.equals(number)) {
					if (m.status == Message.SENT) {
						m.status = Message.DELIVERED;
						changed.add(m);
						data.markDirty();
					}
					if (m.status < Message.READ) {
						unread++;
					}
				}
			}
			if (!changed.isEmpty()) {
				ServerPlayerEntity sender = online(server, data.uuidOf(peer));
				if (sender != null) {
					sendMessages(sender, number, changed, false, false);
				}
			}
			int from = Math.max(0, conv.size() - MAX_SYNC_MESSAGES);
			sendMessages(player, peer, new ArrayList<>(conv.subList(from, conv.size())), false, true);
		}
		if (unread > 0) {
			toast(player, "Tienes " + unread + (unread == 1 ? " mensaje sin leer" : " mensajes sin leer"));
		}
	}

	public static void onDisconnect(ServerPlayerEntity player, MinecraftServer server) {
		calls.onDisconnect(server, player);
	}

	// ---------- peticiones del cliente ----------

	public static void handle(ServerPlayerEntity player, String action, String raw) {
		if (data == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		JsonObject in;
		try {
			JsonElement parsed = JsonParser.parseString(raw == null || raw.isEmpty() ? "{}" : raw);
			in = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			return;
		}
		try {
			switch (action) {
				case "send" -> onSend(server, player, in);
				case "mark_read" -> onMarkRead(server, player, in);
				case "add_contact" -> onAddContact(player, in);
				case "remove_contact" -> onRemoveContact(player, in);
				case "call_start" -> calls.start(server, player, str(in, "number"));
				case "call_answer" -> calls.answer(server, player);
				case "call_decline" -> calls.decline(server, player);
				case "call_hangup" -> calls.hangup(server, player);
				default -> {
				}
			}
		} catch (Exception e) {
			PhoneMod.LOGGER.error("Error procesando acción {}", action, e);
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static void onSend(MinecraftServer server, ServerPlayerEntity player, JsonObject in) {
		String me = data.numberFor(player.getUuid());
		String to = Numbers.normalize(str(in, "to"));
		String text = str(in, "text").trim();
		if (text.isEmpty()) {
			return;
		}
		if (text.length() > MAX_TEXT) {
			text = text.substring(0, MAX_TEXT);
		}
		if (to == null || data.uuidOf(to) == null) {
			toast(player, "Ese número no existe");
			return;
		}
		if (to.equals(me)) {
			toast(player, "No puedes escribirte a ti mismo");
			return;
		}
		ServerPlayerEntity target = online(server, data.uuidOf(to));
		Message m = data.addMessage(me, to, text, target != null ? Message.DELIVERED : Message.SENT);
		sendMessages(player, to, List.of(m), false, false);
		if (target != null) {
			sendMessages(target, me, List.of(m), true, false);
		}
	}

	private static void onMarkRead(MinecraftServer server, ServerPlayerEntity player, JsonObject in) {
		String me = data.numberFor(player.getUuid());
		String peer = Numbers.normalize(str(in, "peer"));
		if (peer == null) {
			return;
		}
		List<Message> changed = new ArrayList<>();
		for (Message m : data.conversation(me, peer)) {
			if (m.to.equals(me) && m.status < Message.READ) {
				m.status = Message.READ;
				changed.add(m);
			}
		}
		if (changed.isEmpty()) {
			return;
		}
		data.markDirty();
		ServerPlayerEntity sender = online(server, data.uuidOf(peer));
		if (sender != null) {
			sendMessages(sender, me, changed, false, false);
		}
	}

	private static void onAddContact(ServerPlayerEntity player, JsonObject in) {
		String me = data.numberFor(player.getUuid());
		String number = Numbers.normalize(str(in, "number"));
		String name = str(in, "name").trim();
		if (name.length() > 24) {
			name = name.substring(0, 24);
		}
		if (name.isEmpty()) {
			toast(player, "Escribe un nombre");
			return;
		}
		if (number == null || data.uuidOf(number) == null) {
			toast(player, "Ese número no existe");
			return;
		}
		if (number.equals(me)) {
			toast(player, "Ese es tu propio número");
			return;
		}
		List<Contact> list = data.contactsOf(player.getUuid().toString());
		Contact existing = null;
		for (Contact c : list) {
			if (c.number.equals(number)) {
				existing = c;
			}
		}
		if (existing != null) {
			existing.name = name;
		} else {
			list.add(new Contact(name, number));
		}
		list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		data.markDirty();
		sendContacts(player);
		toast(player, "Contacto guardado");
	}

	private static void onRemoveContact(ServerPlayerEntity player, JsonObject in) {
		String number = Numbers.normalize(str(in, "number"));
		if (number == null) {
			return;
		}
		data.contactsOf(player.getUuid().toString()).removeIf(c -> c.number.equals(number));
		data.markDirty();
		sendContacts(player);
	}
}
