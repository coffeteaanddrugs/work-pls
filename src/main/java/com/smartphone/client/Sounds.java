package com.smartphone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Sonidos del teléfono (usan sonidos de Minecraft, respetan el volumen de Ajustes). */
public final class Sounds {
	private Sounds() {
	}

	private static void play(RegistryEntry<SoundEvent> sound, float pitch, float volume) {
		float v = volume * ClientSettings.get().volume / 10f;
		if (v <= 0f) {
			return;
		}
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound.value(), pitch, v));
	}

	public static void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f));
	}

	public static void message() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.4f, 1.0f);
	}

	public static void ring(boolean second) {
		play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, second ? 1.26f : 1.0f, 1.0f);
	}

	public static void ringBack() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.5f);
	}

	public static void shutter() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
	}
}
