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
    Snowflake chatChannelId,
    Snowflake achievementChannelId
) {
    public static GlowyBot fromConfig(Path path) {
        try {
            var config = BotConfig.from(path);
            return new GlowyBot(
                DiscordClient.create(config.token),
                Snowflake.of(config.chatChannelId),
                Snowflake.of(config.achievementChannelId)
            );
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read config at path '%s' on enabled with exception:\n%s".formatted(path, e));
        }
    }

    private record BotConfig(
        String token,
        String chatChannelId,
        String achievementChannelId
    ) {
        public static BotConfig from(Path path) throws IOException {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            if (Files.exists(path)) {
                return gson.fromJson(Files.readString(path), BotConfig.class);
            }
            var blankConfig = BotConfig.blank();
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(blankConfig), StandardOpenOption.CREATE_NEW);
            return blankConfig;
        }

        private static BotConfig blank() {
            return new BotConfig("", "", "");
        }
    }
}
