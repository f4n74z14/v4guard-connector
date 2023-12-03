package io.v4guard.plugin.core.check.settings;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.v4guard.plugin.core.CoreInstance;
import io.v4guard.plugin.core.check.PlayerCheckData;
import io.v4guard.plugin.core.constants.SettingsCheckConstants;
import io.v4guard.plugin.core.constants.SettingsKeys;
import io.v4guard.plugin.core.socket.RemoteSettings;
import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerSettingsCheckProcessor {

    private final Cache<UUID, PlayerSettingsCallbackTask> PENDING_CHECKS = Caffeine
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .removalListener((key, value, cause) -> ((PlayerSettingsCallbackTask) value).complete())
            .build();


    public void process(String username, UUID playerUUID, String locale, String viewDistance, String colors,
                        String mainHand, String chatMode, String clientListing, String hat, String cape, String jacket,
                        String leftSleeve, String rightSleeve, String leftPants, String rightPans) {
        if (!CoreInstance.get().getBackend().isReady()) {
            return;
        }

        Document privacySettings = RemoteSettings.getOrDefault(SettingsKeys.PRIVACY, new Document());
        boolean invalidatedCache = RemoteSettings.getOrDefault(SettingsKeys.INVALIDATE_CACHE, false);

        if (invalidatedCache && !privacySettings.getBoolean(SettingsKeys.COLLECT_PLAYER_SETTINGS, true)) {
            return;
        }

        PlayerCheckData checkData = CoreInstance.get().getCheckDataCache().get(username);

        if (checkData == null) {
            return;
        }

        if (checkData.isPlayerSettingsChecked()) {
            return;
        }

        PlayerSettingsCallbackTask task = PENDING_CHECKS.getIfPresent(playerUUID);

        if (task == null) {
            task = new PlayerSettingsCallbackTask(username, checkData);
            task.start();
            PENDING_CHECKS.put(playerUUID, task);
        }

        Map<String, String> settings = new HashMap<>();
        settings.put(SettingsCheckConstants.LOCALE, locale);
        settings.put(SettingsCheckConstants.VIEW_DISTANCE, viewDistance);
        settings.put(SettingsCheckConstants.COLORS, colors);
        settings.put(SettingsCheckConstants.MAIN_HAND, mainHand);
        settings.put(SettingsCheckConstants.CHAT_MODE, chatMode);

        if (clientListing != null) {
            settings.put(SettingsCheckConstants.CLIENT_LISTING_ALLOWED, clientListing);
        }


        Map<String, String> skinParts = new HashMap<>();
        skinParts.put(SettingsCheckConstants.SkinParts.HAT, hat);
        skinParts.put(SettingsCheckConstants.SkinParts.CAPE, cape);
        skinParts.put(SettingsCheckConstants.SkinParts.JACKET, jacket);
        skinParts.put(SettingsCheckConstants.SkinParts.LEFT_SLEEVE, leftSleeve);
        skinParts.put(SettingsCheckConstants.SkinParts.RIGHT_SLEEVE, rightSleeve);
        skinParts.put(SettingsCheckConstants.SkinParts.LEFT_PANTS, leftPants);
        skinParts.put(SettingsCheckConstants.SkinParts.RIGHT_PANTS, rightPans);

        task.setPlayerSettings(settings);
        task.setSkinSettings(skinParts);
    }

    public void onPlayerDisconnect(UUID playerUUID) {
        PENDING_CHECKS.invalidate(playerUUID);
    }

    public void handleTick() {
        PENDING_CHECKS.cleanUp();
    }

}