package glowy.core;

import com.google.common.primitives.Bytes;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.core.spec.WebhookEditSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Image;
import glowy.util.images.Size;
import glowy.util.minecraft.Players;
import glowy.util.plugins.ReactivePlugin;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiFunction;

public final class GlowyPlugin extends ReactivePlugin {
    private final GlowyBot BOT = GlowyBot.of(Path.of("%s/config.json".formatted(getDataFolder())));

    @Override
    public Mono<Void> onInit() {
        return Mono.when(
            forwardPlayerChat(),
            broadcastDiscordChat()
        );
    }

    //TODO runs on game logic thread, need to move so reading avatar doesn't run on game thread.
    private Mono<Void> forwardPlayerChat() {
        BiFunction<Webhook, AsyncPlayerChatEvent, Mono<Webhook>> editHook = (hook, event) -> {
            String playerName = event.getPlayer().getName();
            return hook.getName()
                .filter(name -> name.equals(playerName))
                .map(name -> Mono.just(hook))
                .orElseGet(() -> {
                    var hookEdit = WebhookEditSpec.builder().name(playerName);
                    try {
                        hookEdit.avatar(Image.ofRaw(Bytes.toArray(Players.getPlayerAvatar(event.getPlayer(), Size.of(512, 512))), Image.Format.PNG));
                    }
                    catch (IOException e) {
                        hookEdit.avatar(Possible.absent());
                    }
                    return hook.edit(hookEdit.build());
                });
        };
        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.chatChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> channel.createWebhook(WebhookCreateSpec.builder().name("glowy chat hook").build()))
            .flatMap(hook -> Mono.just(hook)
                .doOnCancel(() -> hook.delete().block()))
            .flatMap(hook -> on(AsyncPlayerChatEvent.class)
                .flatMap(event -> editHook.apply(hook, event)
                    .flatMap(__ -> hook.execute(WebhookExecuteSpec.builder()
                        .content("%s".formatted(event.getMessage()))
                        .build())))
            .then()));
    }

    private Mono<Void> broadcastDiscordChat() {
        return BOT.client().withGateway(gateway -> gateway.on(MessageCreateEvent.class)
            .filter(event -> event.getMessage().getChannelId().equals(BOT.chatChannelId()))
            .flatMap(event -> Mono.fromRunnable(() -> event.getMember()
                .ifPresent(author -> getServer().broadcastMessage("<@%s> %s".formatted(author.getDisplayName(), event.getMessage().getContent()))))));
    }
}
