package glowy.util.minecraft;

import discord4j.rest.util.Image;
import org.bukkit.entity.Player;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;
import java.util.function.Function;

public final class Players {
    private Players(){}

    public static Mono<Image> getAvatar(Player player, int width, int height) {
        Callable<Image> avatarFromSkin = () -> {
            Function<BufferedImage, BufferedImage> asAvatar = skinImage -> {
                var avatar = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var avatarGraphics = avatar.createGraphics();
                avatarGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                avatarGraphics.drawImage(skinImage.getSubimage(8, 8, 8, 8), 0, 0, width, height, null);
                avatarGraphics.dispose();
                return avatar;
            };
            try (var skinIn = player.getPlayerProfile().getTextures().getSkin().openStream()) {
                var out = new ByteArrayOutputStream();
                ImageIO.write(asAvatar.apply(ImageIO.read(skinIn)), "png", out);
                return Image.ofRaw(out.toByteArray(), Image.Format.PNG);
            }
        };
        return Mono.fromCallable(avatarFromSkin)
            .onErrorResume(ignored -> Image.ofUrl("https://files.catbox.moe/72azs6.png"));
    }
}
