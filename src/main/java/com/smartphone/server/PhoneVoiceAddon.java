package com.smartphone.server;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon de Plasmo Voice. Crea una "activación" llamada Teléfono: lo que hablas con ella
 * solo lo escucha la persona con la que estás en llamada, sin importar la distancia.
 */
@Addon(
		id = "pv-addon-smartphone",
		name = "Smartphone",
		version = "1.0.0",
		authors = {"Smartphone"}
)
public final class PhoneVoiceAddon implements AddonInitializer {
	private static PhoneVoiceAddon instance;

	@InjectPlasmoVoice
	private PlasmoVoiceServer voiceServer;

	private ServerSourceLine line;
	private ServerActivation activation;

	/** jugador que habla -> jugador que escucha */
	private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
	/** jugador que escucha -> fuente de audio dirigida solo a él */
	private final Map<UUID, ServerDirectSource> sources = new ConcurrentHashMap<>();

	@Override
	public void onAddonInitialize() {
		instance = this;

		line = voiceServer.getSourceLineManager().createBuilder(
				this,
				"phone",
				"Teléfono",
				"plasmovoice:textures/icons/speaker.png",
				5
		).build();

		activation = voiceServer.getActivationManager().createBuilder(
				this,
				"phone",
				"Teléfono",
				"plasmovoice:textures/icons/microphone.png",
				"pv.activation.proximity", // reutiliza el permiso normal de hablar
				1000
		).build();

		activation.onPlayerActivationStart(player -> ServerActivation.Result.HANDLED);

		activation.onPlayerActivation((player, packet) -> {
			ServerDirectSource source = sourceFor(player);
			if (source != null) {
				source.sendAudioFrame(packet.getData(), packet.getSequenceNumber());
			}
			return ServerActivation.Result.HANDLED;
		});

		activation.onPlayerActivationEnd((player, packet) -> {
			ServerDirectSource source = sourceFor(player);
			if (source != null) {
				source.sendAudioEnd(packet.getSequenceNumber());
			}
			return ServerActivation.Result.HANDLED;
		});
	}

	@Override
	public void onAddonShutdown() {
		for (ServerDirectSource s : sources.values()) {
			s.remove();
		}
		sources.clear();
		partners.clear();
		instance = null;
	}

	/** Fuente de audio del interlocutor del jugador que está hablando (la crea la primera vez). */
	private ServerDirectSource sourceFor(VoicePlayer speaker) {
		UUID speakerId = speaker.getInstance().getUUID();
		UUID listenerId = partners.get(speakerId);
		if (listenerId == null) {
			return null;
		}
		ServerDirectSource existing = sources.get(listenerId);
		if (existing != null) {
			return existing;
		}
		VoicePlayer listener = voiceServer.getPlayerManager().getPlayerById(listenerId).orElse(null);
		if (listener == null) {
			return null;
		}
		ServerDirectSource created = line.createDirectSource(listener, false);
		sources.put(listenerId, created);
		return created;
	}

	public static void startCall(UUID a, UUID b) {
		if (instance == null) {
			return;
		}
		instance.partners.put(a, b);
		instance.partners.put(b, a);
	}

	public static void endCall(UUID a, UUID b) {
		if (instance == null) {
			return;
		}
		instance.partners.remove(a);
		instance.partners.remove(b);
		for (UUID id : new UUID[]{a, b}) {
			ServerDirectSource s = instance.sources.remove(id);
			if (s != null) {
				s.remove();
			}
		}
	}
}
