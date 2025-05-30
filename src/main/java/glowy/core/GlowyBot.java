package glowy.core;

import com.google.gson.GsonBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record GlowyBot(
    DiscordClient client,
    Snowflake chatChannelId
) {
    public static GlowyBot config(Path path) {
        record BotConfig(
            String token,
            String chatChannelId
        ){}
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                var config = gson.fromJson(Files.readString(path), BotConfig.class);
                return new GlowyBot(
                    DiscordClient.create(config.token),
                    Snowflake.of(config.chatChannelId)
                );
            }
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                gson.toJson(new BotConfig("", "")),
                StandardOpenOption.CREATE_NEW
            );
            throw new IllegalStateException("First startup, please configure at path '%s'".formatted(path));
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read config at path '%s' on enabled with exception:\n%s".formatted(path, e));
        }
    }
}
