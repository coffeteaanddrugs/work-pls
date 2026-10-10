package com.smartphone.server;

import com.smartphone.PhoneMod;

import java.util.UUID;

/** Puente hacia Plasmo Voice. Si algo falla con el audio, el resto del teléfono sigue funcionando. */
public final class VoiceBridge {
	private static boolean warned;

	private VoiceBridge() {
	}

	public static void start(UUID a, UUID b) {
		try {
			PhoneVoiceAddon.startCall(a, b);
		} catch (Throwable t) {
			warn(t);
		}
	}

	public static void end(UUID a, UUID b) {
		try {
			PhoneVoiceAddon.endCall(a, b);
		} catch (Throwable t) {
			warn(t);
		}
	}

	private static void warn(Throwable t) {
		if (!warned) {
			warned = true;
			PhoneMod.LOGGER.error("Plasmo Voice no está disponible: las llamadas no tendrán audio", t);
		}
	}
}
