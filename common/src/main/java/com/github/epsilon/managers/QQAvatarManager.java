package com.github.epsilon.managers;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.utils.network.Http;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.epsilon.Constants.mc;

public class QQAvatarManager {

    public static final QQAvatarManager INSTANCE = new QQAvatarManager();

    private enum State {
        UNINITIALIZED,
        LOADING,
        READY,
        FAILED
    }

    private static final Identifier AVATAR_TEXTURE = ResourceLocationUtils.getIdentifier("qq/avatar");
    private static final Identifier FALLBACK_TEXTURE = ResourceLocationUtils.getIdentifier("textures/gui/steve.png");
    private static final Pattern QQ_PARTITION = Pattern.compile("qqnt_([1-9][0-9]{5,11})");
    private static final Pattern QQ_DIRECTORY = Pattern.compile("([1-9][0-9]{5,11})");
    private static final int TEXTURE_SIZE = 128;

    private DynamicTexture avatarTexture;
    private State state = State.UNINITIALIZED;
    private long generation;

    private QQAvatarManager() {
    }

    public Identifier texture() {
        return state == State.READY ? AVATAR_TEXTURE : FALLBACK_TEXTURE;
    }

    public boolean linearFilter() {
        return state == State.READY;
    }

    public void requestLoad() {
        if (state != State.UNINITIALIZED) return;

        String qq = findLocalAccount();
        if (qq == null) {
            state = State.FAILED;
            return;
        }

        state = State.LOADING;
        long requestGeneration = ++generation;
        ExecutorManager.INSTANCE.execute(() -> loadAvatar(qq, requestGeneration));
    }

    public void release() {
        generation++;
        state = State.UNINITIALIZED;
        if (avatarTexture != null) {
            mc.getTextureManager().release(AVATAR_TEXTURE);
            avatarTexture = null;
        }
    }

    private void loadAvatar(String qq, long requestGeneration) {
        BufferedImage image;
        try {
            image = downloadAvatar(qq);
        } catch (IOException | RuntimeException e) {
            mc.execute(() -> fail(requestGeneration));
            return;
        }

        mc.execute(() -> uploadAvatar(image, requestGeneration));
    }

    private void uploadAvatar(BufferedImage source, long requestGeneration) {
        if (requestGeneration != generation || state != State.LOADING) return;

        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            image = toNativeImage(source);
            texture = new DynamicTexture(() -> "Epsilon local QQ avatar", image);
            image = null;
            mc.getTextureManager().register(AVATAR_TEXTURE, texture);
            avatarTexture = texture;
            texture = null;
            state = State.READY;
        } catch (RuntimeException e) {
            if (texture != null) {
                texture.close();
            } else if (image != null) {
                image.close();
            }
            fail(requestGeneration);
        }
    }

    private void fail(long requestGeneration) {
        if (requestGeneration == generation && state == State.LOADING) {
            state = State.FAILED;
        }
    }

    private static BufferedImage downloadAvatar(String qq) throws IOException {
        String url = "https://q1.qlogo.cn/g?b=qq&nk=" + qq + "&s=640";
        InputStream response = Http.get(url).exceptionHandler(ignored -> {
        }).sendInputStream();
        if (response == null) throw new IOException("QQ avatar request failed");

        try (response) {
            BufferedImage image = ImageIO.read(response);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IOException("Unsupported QQ avatar image");
            }
            return image;
        }
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - cropSize) / 2;
        int cropY = (source.getHeight() - cropSize) / 2;
        BufferedImage normalized = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, cropX, cropY, cropX + cropSize, cropY + cropSize, null);
        graphics.dispose();

        NativeImage result = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        try {
            for (int y = 0; y < TEXTURE_SIZE; y++) {
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    result.setPixel(x, y, normalized.getRGB(x, y));
                }
            }
            return result;
        } catch (RuntimeException e) {
            result.close();
            throw e;
        }
    }

    private static String findLocalAccount() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            String account = newestMatchingDirectory(Path.of(appData, "QQ", "Partitions"), QQ_PARTITION);
            if (account != null) return account;
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) return null;
        return newestMatchingDirectory(Path.of(userHome, "Documents", "Tencent Files"), QQ_DIRECTORY);
    }

    private static String newestMatchingDirectory(Path parent, Pattern pattern) {
        if (!Files.isDirectory(parent)) return null;

        String newestAccount = null;
        long newestModified = Long.MIN_VALUE;
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(parent, Files::isDirectory)) {
            for (Path directory : directories) {
                Path fileName = directory.getFileName();
                if (fileName == null) continue;

                Matcher matcher = pattern.matcher(fileName.toString());
                if (!matcher.matches()) continue;

                long modified = Files.getLastModifiedTime(directory).toMillis();
                if (modified > newestModified) {
                    newestModified = modified;
                    newestAccount = matcher.group(1);
                }
            }
        } catch (IOException | SecurityException ignored) {
            return null;
        }
        return newestAccount;
    }

}
