package com.smartphone.server;

import com.google.gson.JsonObject;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Numbers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Estado de las llamadas entre jugadores (quién llama a quién, timbre, contestar, colgar). */
public final class CallManager {
	private static final long RING_TIMEOUT_MS = 30_000;

	private static final class Call {
		final UUID caller;
		final UUID callee;
		final long created = System.currentTimeMillis();
		boolean active;
		long answered;

		Call(UUID caller, UUID callee) {
			this.caller = caller;
			this.callee = callee;
		}
	}

	private final Map<UUID, Call> calls = new HashMap<>();

	private static PhoneData data() {
		return ServerHandler.data;
	}

	private static void sendCall(ServerPlayerEntity p, String state, String number, String reason) {
		JsonObject o = new JsonObject();
		o.addProperty("state", state);
		o.addProperty("number", number);
		o.addProperty("reason", reason);
		ServerHandler.send(p, "call", o);
	}

	/** Intento fallido de llamada (no existe, ocupado, desconectado...). */
	private void failed(ServerPlayerEntity caller, String number, String result) {
		data().addLog(caller.getUuid().toString(), new CallLogEntry(number, "out", System.currentTimeMillis(), 0, result));
		sendCall(caller, "ended", number, result);
		ServerHandler.sendCallLog(caller);
	}

	public void start(MinecraftServer server, ServerPlayerEntity caller, String rawNumber) {
		if (calls.containsKey(caller.getUuid())) {
			return;
		}
		String number = Numbers.normalize(rawNumber);
		String myNumber = data().numberFor(caller.getUuid());
		if (number == null || data().uuidOf(number) == null) {
			ServerHandler.toast(caller, "Ese número no existe");
			return;
		}
		if (number.equals(myNumber)) {
			ServerHandler.toast(caller, "No puedes llamarte a ti mismo");
			return;
		}
		ServerPlayerEntity callee = ServerHandler.online(server, data().uuidOf(number));
		if (callee == null) {
			failed(caller, number, "unavailable");
			return;
		}
		if (calls.containsKey(callee.getUuid())) {
			failed(caller, number, "busy");
			return;
		}
		Call call = new Call(caller.getUuid(), callee.getUuid());
		calls.put(caller.getUuid(), call);
		calls.put(callee.getUuid(), call);
		sendCall(caller, "outgoing", number, "");
		sendCall(callee, "incoming", myNumber, "");
	}

	public void answer(MinecraftServer server, ServerPlayerEntity player) {
		Call call = calls.get(player.getUuid());
		if (call == null || call.active || !call.callee.equals(player.getUuid())) {
			return;
		}
		call.active = true;
		call.answered = System.currentTimeMillis();
		ServerPlayerEntity caller = server.getPlayerManager().getPlayer(call.caller);
		if (caller != null) {
			sendCall(caller, "active", data().numberFor(call.callee), "");
		}
		sendCall(player, "active", data().numberFor(call.caller), "");
		VoiceBridge.start(call.caller, call.callee);
	}

	public void decline(MinecraftServer server, ServerPlayerEntity player) {
		Call call = calls.get(player.getUuid());
		if (call == null) {
			return;
		}
		if (!call.active && call.callee.equals(player.getUuid())) {
			finish(server, call, "declined");
		} else {
			hangup(server, player);
		}
	}

	public void hangup(MinecraftServer server, ServerPlayerEntity player) {
		Call call = calls.get(player.getUuid());
		if (call == null) {
			return;
		}
		finish(server, call, call.active ? "answered" : "missed");
	}

	public void onDisconnect(MinecraftServer server, ServerPlayerEntity player) {
		Call call = calls.get(player.getUuid());
		if (call != null) {
			finish(server, call, call.active ? "answered" : "missed");
		}
	}

	public void tick(MinecraftServer server) {
		if (calls.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Call call : new ArrayList<>(calls.values())) {
			if (!call.active && now - call.created > RING_TIMEOUT_MS && calls.containsKey(call.caller)) {
				finish(server, call, "missed");
			}
		}
	}

	public void endAll(MinecraftServer server) {
		for (Call call : new ArrayList<>(calls.values())) {
			if (calls.containsKey(call.caller)) {
				finish(server, call, call.active ? "answered" : "missed");
			}
		}
	}

	private void finish(MinecraftServer server, Call call, String result) {
		calls.remove(call.caller);
		calls.remove(call.callee);
		if (call.active) {
			VoiceBridge.end(call.caller, call.callee);
		}
		long now = System.currentTimeMillis();
		int duration = call.active ? (int) ((now - call.answered) / 1000) : 0;
		String callerNumber = data().numberFor(call.caller);
		String calleeNumber = data().numberFor(call.callee);
		data().addLog(call.caller.toString(), new CallLogEntry(calleeNumber, "out", now, duration, result));
		data().addLog(call.callee.toString(), new CallLogEntry(callerNumber, "in", now, duration, result));

		String reason = result.equals("answered") ? "ended" : result;
		ServerPlayerEntity caller = server.getPlayerManager().getPlayer(call.caller);
		ServerPlayerEntity callee = server.getPlayerManager().getPlayer(call.callee);
		if (caller != null) {
			sendCall(caller, "ended", calleeNumber, reason);
			ServerHandler.sendCallLog(caller);
		}
		if (callee != null) {
			sendCall(callee, "ended", callerNumber, reason);
			ServerHandler.sendCallLog(callee);
		}
	}
}
