package glowy.util.minecraft;

import com.google.common.primitives.Bytes;
import glowy.util.images.Size;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public class Players {
    public static List<Byte> getPlayerAvatar(Player player, Size size) throws IOException, NullPointerException {
        Function<BufferedImage, BufferedImage> asAvatar = skinImage -> {
            var avatar = new BufferedImage(size.width(), size.height(), BufferedImage.TYPE_INT_ARGB);
            var avatarGraphics = avatar.createGraphics();
            avatarGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            avatarGraphics.drawImage(skinImage.getSubimage(8, 8, 8, 8), 0, 0, size.width(), size.height(), null);
            avatarGraphics.dispose();
            return avatar;
        };
        try (var skinIn = player.getPlayerProfile().getTextures().getSkin().openStream()) {
            var out = new ByteArrayOutputStream();
            ImageIO.write(asAvatar.apply(ImageIO.read(skinIn)), "png", out);
            return Bytes.asList(out.toByteArray());
        }
    }
}
