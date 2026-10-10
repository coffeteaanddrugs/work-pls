package com.smartphone;

import com.smartphone.net.PhoneC2S;
import com.smartphone.net.PhoneS2C;
import com.smartphone.server.PhoneVoiceAddon;
import com.smartphone.server.ServerHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.server.PlasmoVoiceServer;

public class PhoneMod implements ModInitializer {
	public static final String MOD_ID = "smartphone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(PhoneC2S.ID, PhoneC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(PhoneS2C.ID, PhoneS2C.CODEC);

		ServerLifecycleEvents.SERVER_STARTING.register(ServerHandler::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(ServerHandler::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(ServerHandler::onTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerHandler.onJoin(handler.player, server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerHandler.onDisconnect(handler.player, server));

		ServerPlayNetworking.registerGlobalReceiver(PhoneC2S.ID,
				(payload, context) -> ServerHandler.handle(context.player(), payload.action(), payload.data()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("phone")
						.then(CommandManager.literal("number").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							String number = ServerHandler.data.numberFor(p.getUuid());
							ctx.getSource().sendFeedback(() -> Text.literal("Tu número de teléfono es " + number), false);
							return 1;
						}))));

		// Registra el addon de voz (llamadas)
		PlasmoVoiceServer.getAddonsLoader().load(new PhoneVoiceAddon());
		LOGGER.info("Smartphone cargado");
	}
}
