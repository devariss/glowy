package glowy.core;

import com.google.gson.GsonBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidParameterException;

public record GlowyBot(
    DiscordClient client,
    Snowflake chatChannelId
) {
    public static GlowyBot of(Path path) {
        try {
            var config = BotConfig.from(path);
            return new GlowyBot(
                DiscordClient.create(config.token),
                Snowflake.of(config.chatChannelId)
            );
        }
        catch (InvalidParameterException e) {
            throw new IllegalStateException("First startup, please configure at path '%s'".formatted(path));
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read config at path '%s' on enabled with exception:\n%s".formatted(path, e));
        }
    }

    private record BotConfig(
        String token,
        String chatChannelId
    ) {
        public static BotConfig from(Path path) throws IOException, InvalidParameterException {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            if (Files.exists(path)) {
                return gson.fromJson(Files.readString(path), BotConfig.class);
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(BotConfig.blank()), StandardOpenOption.CREATE_NEW);
            throw new InvalidParameterException();
        }

        private static BotConfig blank() {
            return new BotConfig("", "");
        }
    }
}
