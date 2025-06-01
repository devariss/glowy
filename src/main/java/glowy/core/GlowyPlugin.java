package glowy.core;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.Image;
import glowy.util.minecraft.Players;
import glowy.util.plugins.ReactivePlugin;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.function.Function3;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GlowyPlugin extends ReactivePlugin {
    private final GlowyBot BOT = GlowyBot.fromConfig(Path.of("%s/config.json".formatted(getDataFolder())));

    @Override
    public Mono<Void> onInit() {
        return pluginTasks();
    }

    private Mono<Void> forwardPlayerAchievements() {
        Function<AdvancementDisplay, String> formattedContent =
            display ->
                """
                has made the advancement ***[%s]***
                >>> *%s*
                """.formatted(display.getTitle(), display.getDescription());

        Predicate<Advancement> isDisplayed =
            advancement -> Optional.ofNullable(advancement.getDisplay())
                .filter(AdvancementDisplay::shouldAnnounceChat)
                .isPresent();

        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.achievementChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> on(PlayerAdvancementDoneEvent.class)
                .filter(event -> isDisplayed.test(event.getAdvancement()))
                .flatMap(event -> Players.getAvatar(event.getPlayer(), 512, 512)
                    .flatMap(avatar -> channel.createWebhook(WebhookCreateSpec.builder()
                        .name(event.getPlayer().getName())
                        .avatar(Possible.of(Optional.of(avatar)))
                        .build()))
                    .flatMap(hook -> hook.execute(WebhookExecuteSpec.builder()
                        .content(formattedContent.apply(event.getAdvancement().getDisplay()))
                        .build())
                        .then(hook.delete())))
            .then()));
    }

    private Mono<Void> forwardPlayerChat() {
        Sinks.Many<Webhook> latestHookSink = Sinks.many().replay().latest();

        Function3<TextChannel, String, Possible<Optional<Image>>, Mono<Webhook>> emitHook =
            (channel, name, possibleAvatar) -> channel.createWebhook(WebhookCreateSpec.builder()
                    .name(name)
                    .avatar(possibleAvatar)
                    .build())
                .doOnNext(latestHookSink::tryEmitNext);

        BiFunction<TextChannel, Player, Mono<Webhook>> filterLatestHook =
            (channel, player) -> latestHookSink.asFlux()
                .next()
                .flatMap(latestHook -> Mono.just(latestHook)
                    .filter(__ -> latestHook.getName().orElse("").equals(player.getName()))
                    .switchIfEmpty(latestHook.delete()
                        .then(Players.getAvatar(player, 512, 512))
                        .flatMap(avatar -> emitHook.apply(channel, player.getName(), Possible.of(Optional.of(avatar))))));

        return BOT.client().withGateway(gateway -> gateway.getChannelById(BOT.chatChannelId())
            .cast(TextChannel.class)
            .flatMap(channel -> emitHook.apply(channel, "Glowy Chat Hook", Possible.of(Optional.empty()))
                .thenMany(on(AsyncPlayerChatEvent.class))
                .flatMap(event -> filterLatestHook.apply(channel, event.getPlayer())
                .flatMap(hook -> hook.execute(WebhookExecuteSpec.builder()
                    .content("%s".formatted(event.getMessage()))
                    .build())))
                .then()
                .doOnCancel(() -> latestHookSink.asFlux().next().block().delete().block())));
    }

    private Mono<Void> broadcastChannelChat() {
        return BOT.client().withGateway(gateway -> gateway.on(MessageCreateEvent.class)
            .filter(event -> event.getMessage().getChannelId().equals(BOT.chatChannelId()))
            .map(event -> event.getMember()
                .map(author -> getServer().broadcastMessage("<@%s> %s".formatted(author.getDisplayName(), event.getMessage().getContent())))));
    }

    private Mono<Void> pluginTasks() {
        return Mono.when(
            forwardPlayerAchievements(),
            forwardPlayerChat(),
            broadcastChannelChat()
        );
    }
}
