package com.smartphone.client;

import com.smartphone.net.PhoneS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PhoneClient implements ClientModInitializer {
	public static KeyBinding openKey;
	public static KeyBinding answerKey;
	public static KeyBinding hangupKey;
	public static KeyBinding photoKey;

	private static final String CATEGORY = "key.categories.smartphone";

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
		answerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.answer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
		hangupKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.hangup", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
		photoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.photo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(PhoneS2C.ID,
				(payload, context) -> ClientPacketHandler.handle(payload.action(), payload.data()));

		HudRenderCallback.EVENT.register(HudOverlay::render);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPhoneState.reset();
			CameraMode.exit();
		});

		ClientTickEvents.END_CLIENT_TICK.register(PhoneClient::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		while (openKey.wasPressed()) {
			if (CameraMode.active) {
				CameraMode.exit();
			}
			if (client.currentScreen == null) {
				client.setScreen(new PhoneScreen());
			}
		}
		while (answerKey.wasPressed()) {
			if (ClientPhoneState.callState.equals("incoming")) {
				ClientPhoneState.send("call_answer");
			}
		}
		while (hangupKey.wasPressed()) {
			switch (ClientPhoneState.callState) {
				case "incoming" -> ClientPhoneState.send("call_decline");
				case "outgoing", "active" -> ClientPhoneState.send("call_hangup");
				default -> {
				}
			}
		}
		while (photoKey.wasPressed()) {
			CameraMode.shoot();
		}

		CameraMode.tick(client);
		ringtone();
	}

	/** Timbre de llamada entrante y tono de llamada saliente. */
	private static void ringtone() {
		String state = ClientPhoneState.callState;
		if (state.equals("incoming") && !ClientSettings.get().dnd) {
			int t = ClientPhoneState.callTicks++ % 50;
			if (t == 0) {
				Sounds.ring(false);
			} else if (t == 4) {
				Sounds.ring(true);
			} else if (t == 8) {
				Sounds.ring(false);
			} else if (t == 12) {
				Sounds.ring(true);
			}
		} else if (state.equals("outgoing")) {
			if (ClientPhoneState.callTicks++ % 60 == 0) {
				Sounds.ringBack();
			}
		}
	}
}
