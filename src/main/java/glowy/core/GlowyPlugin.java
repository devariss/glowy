package glowy.core;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.TextChannel;
import glowy.util.plugins.ReactivePlugin;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public final class GlowyPlugin extends ReactivePlugin {
    @Override
    public Mono<Void> onInit() {
        var bot = GlowyBot.config(Path.of("%s/config.json".formatted(getDataFolder())));
        return Mono.when(
            broadcastDiscordChat(bot),
            forwardPlayerChat(bot)
        );
    }

    // implement
    private Mono<Void> forwardPlayerChat(GlowyBot bot) {
        return Mono.empty();
    }

    private Mono<Void> broadcastDiscordChat(GlowyBot bot) {
        return bot.client().withGateway(gateway -> gateway.on(MessageCreateEvent.class)
            .filter(event -> event.getMessage().getChannelId().equals(bot.chatChannelId()))
            .flatMap(event -> Mono.fromRunnable(() -> event.getMember()
                .ifPresent(author -> getServer().broadcastMessage("<@%s> %s".formatted(author.getDisplayName(), event.getMessage().getContent()))))));
    }
}
