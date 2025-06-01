package glowy;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.UserEditSpec;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Image;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;

public final class GlowyPlugin extends ReactivePlugin {
    private final GlowyBot BOT = GlowyBot.fromConfig(Path.of("%s/config.json".formatted(getDataFolder())));

    @Override
    public Mono<Void> onInit() {
        return pluginTasks()
            .doOnCancel(() -> BOT.client().withGateway(GatewayDiscordClient::logout).block());
    }

    private Mono<Void> botStartup() {
        return Image.ofUrl("https://files.catbox.moe/txmh30.png")
            .flatMap(avatarUrl -> BOT.client().withGateway(gateway -> gateway.edit(UserEditSpec.builder()
                .avatar(avatarUrl)
                .username("Glowy")
                .build())));
    }

    //TODO implement.
    private Mono<Void> forwardPlayerAchievements() {
        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.achievementChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> Image.ofUrl("https://files.catbox.moe/5dbkcw.png") // add url to achievement hook pfp
                .flatMap(avatarUrl -> channel.createWebhook(WebhookCreateSpec.builder()
                    .name("Achievements")
                    .avatar(Possible.of(Optional.of(avatarUrl)))
                    .build())))
            .flatMap(hook -> on(PlayerAdvancementDoneEvent.class)
                .doOnCancel(() -> hook.delete().block())
                .flatMap(event -> Mono.empty())
                .then()));
    }

    //TODO create new webhook on new player chatting and deleting old hook
    private Mono<Void> forwardPlayerChat() {
        Sinks.Many<Webhook> latestHook = Sinks.many().replay().latest();

        BiFunction<TextChannel, Player, Mono<Webhook>> createHook =
            (channel, player) -> Mono.fromCallable(() -> Image.ofRaw(Players.getPlayerAvatar(player, Images.Size.of(512, 512)), Image.Format.PNG))
                .onErrorResume(e -> {
                    getLogger().warning("Failed to get player '%s's avatar with exception:\n%s".formatted(player.getName(), e));
                    return Image.ofUrl("https://files.catbox.moe/72azs6.png");
                })
                .flatMap(avatarImage -> channel.createWebhook(WebhookCreateSpec.builder()
                    .name(player.getName())
                    .avatar(Possible.of(Optional.of(avatarImage)))
                    .build()));

        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.chatChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> on(AsyncPlayerChatEvent.class)
                .flatMap(event -> latestHook.asFlux()
                    .defaultIfEmpty(createHook.apply(channel, event.getPlayer())))
                .then()));
        /*
        BiFunction<Webhook, String, Mono<Webhook>> editHook = (hook, playerName) -> hook.getName()
            .filter(name -> name.equals(playerName))
            .map(name -> Mono.just(hook))
            .orElseGet(() -> Mono.just(WebhookEditSpec.builder().name(playerName))
                .flatMap(edit -> Mono.fromCallable(() -> edit.avatar(Image.ofRaw(
                        Players.getPlayerAvatar(Bukkit.getPlayerExact(playerName), Images.Size.of(512, 512)),
                        Image.Format.PNG
                    )))
                    .onErrorResume(e -> {
                        getLogger().warning("Failed to get avatar from player '%s' with exception:\n%s".formatted(playerName, e));
                        return Mono.just(edit.avatar(Possible.absent()));
                    }))
                    .flatMap(edit -> hook.edit(edit.build())));

        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.chatChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> channel.createWebhook(WebhookCreateSpec.builder()
                .name("Chat Hook")
                .build()))
            .flatMap(hook -> on(AsyncPlayerChatEvent.class)
                .doOnCancel(() -> hook.delete().block())
                .flatMap(event -> editHook.apply(hook, event.getPlayer().getName())
                    .flatMap(__ -> hook.execute(WebhookExecuteSpec.builder()
                        .content("%s".formatted(event.getMessage()))
                        .build())))
            .then()));

         */
    }

    private Mono<Void> broadcastChannelChat() {
        return BOT.client().withGateway(gateway -> gateway.on(MessageCreateEvent.class)
            .filter(event -> event.getMessage().getChannelId().equals(BOT.chatChannelId()))
            .map(event -> event.getMember()
                .map(author -> getServer().broadcastMessage("<@%s> %s".formatted(author.getDisplayName(), event.getMessage().getContent())))));
    }

    private Mono<Void> pluginTasks() {
        return Mono.when(
            botStartup(),
            // forwardPlayerAchievements(),
            forwardPlayerChat(),
            broadcastChannelChat()
        );
    }
}
