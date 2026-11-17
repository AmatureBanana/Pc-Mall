package cn.zhuangjimao.auth;

import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.io.*;
import java.awt.image.BufferedImage;

@Service
class AccountAvatar {
    private final AvatarMapper avatars;
    private final AccountMapper accounts;
    AccountAvatar(AvatarMapper avatars, AccountMapper accounts) { this.avatars = avatars; this.accounts = accounts; }

    byte[] read(long id) { var row = avatars.image(id); return row == null ? null : row.image(); }

    @Transactional
    public Account.Summary upload(AccountPrincipal principal, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) throw AccountInputs.invalid("头像须为不超过2MB的PNG或JPEG图片。");
        byte[] normalized;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw AccountInputs.invalid("请选择有效的PNG或JPEG图片。");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    throw AccountInputs.invalid("头像仅支持PNG、JPEG，尺寸不超过4096×4096。");
                }
                var source = reader.read(0);
                var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    int side = Math.min(source.getWidth(), source.getHeight());
                    int x = (source.getWidth() - side) / 2, y = (source.getHeight() - side) / 2;
                    graphics.drawImage(source, 0, 0, 256, 256, x, y, x + side, y + side, null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                normalized = output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) { throw AccountInputs.invalid("图片无法读取，请重新选择PNG或JPEG图片。"); }
        var account = accounts.lockById(principal.accountId());
        if (!principal.matches(account)) throw new AuthException(401, "UNAUTHENTICATED", "登录已过期，请重新登录。");
        avatars.save(account.id(), normalized);
        return account.summary();
    }
}

@Mapper
interface AvatarMapper {
    @Select("SELECT image FROM account_avatar WHERE account_id=#{id}")
    AvatarImage image(long id);
    record AvatarImage(byte[] image) {}
    @Insert("INSERT INTO account_avatar(account_id,image) VALUES(#{id},#{image}) ON DUPLICATE KEY UPDATE image=VALUES(image)")
    void save(long id, byte[] image);
}
