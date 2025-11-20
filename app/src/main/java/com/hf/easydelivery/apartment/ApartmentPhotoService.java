package com.hf.easydelivery.apartment;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.ApartmentPhotoEntity;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ApartmentPhotoService {

    public static class MatchResult {
        public final ApartmentPhotoEntity entity;
        public final File file;
        public final boolean fuzzy;

        MatchResult(ApartmentPhotoEntity entity, File file, boolean fuzzy) {
            this.entity = entity;
            this.file = file;
            this.fuzzy = fuzzy;
        }
    }

    private static final String TAG = "ApartmentPhotoService";

    private static ApartmentPhotoService instance;

    public static synchronized ApartmentPhotoService getInstance(Context context) {
        if (instance == null) {
            instance = new ApartmentPhotoService(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final ApartmentPhotoRepository repository;
    private final File storageDir;

    private ApartmentPhotoService(Context context) {
        this.appContext = context;
        this.repository = new ApartmentPhotoRepository();
        this.storageDir = new File(context.getFilesDir(), "apartment_photos");
        if (!storageDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            storageDir.mkdirs();
        }
    }

    public ApartmentAddressKeyBuilder.KeyData buildKeyData(@Nullable DeliveryInfo info) {
        return ApartmentAddressKeyBuilder.fromDelivery(info);
    }

    public boolean hasPhotoForKey(@Nullable String key) {
        if (TextUtils.isEmpty(key)) return false;
        ApartmentPhotoEntity entity = repository.findByKey(key);
        return entity != null && !TextUtils.isEmpty(entity.filePath);
    }

    @Nullable
    public MatchResult findMatch(@Nullable DeliveryInfo info) {
        if (info == null) return null;
        ApartmentAddressKeyBuilder.KeyData keyData = buildKeyData(info);
        if (!TextUtils.isEmpty(keyData.key)) {
            MatchResult match = validateEntity(repository.findByKey(keyData.key), false);
            if (match != null) {
                repository.updateLastUsed(match.entity.id, System.currentTimeMillis());
                return match;
            }
        }
        return fuzzyMatch(info.getAddress());
    }

    private MatchResult fuzzyMatch(@Nullable String fullAddress) {
        if (TextUtils.isEmpty(fullAddress)) return null;
        String normalized = fullAddress.toLowerCase(Locale.US);
        List<ApartmentPhotoEntity> all = repository.listAll();
        if (all == null || all.isEmpty()) return null;
        for (ApartmentPhotoEntity entity : all) {
            if (entity == null || TextUtils.isEmpty(entity.addressKey)) continue;
            if (!ApartmentPhotoEntity.SOURCE_MANUAL.equals(entity.source)) continue;
            String key = entity.addressKey.toLowerCase(Locale.US);
            if (normalized.contains(key)) {
                MatchResult match = validateEntity(entity, true);
                if (match != null) {
                    repository.updateLastUsed(match.entity.id, System.currentTimeMillis());
                    return match;
                }
            }
        }
        return null;
    }

    @Nullable
    private MatchResult validateEntity(@Nullable ApartmentPhotoEntity entity, boolean fuzzy) {
        if (entity == null || TextUtils.isEmpty(entity.filePath)) {
            return null;
        }
        File file = new File(entity.filePath);
        if (!file.exists()) {
            repository.delete(entity.id);
            return null;
        }
        return new MatchResult(entity, file, fuzzy);
    }

    public boolean savePhoto(File sourceFile,
                             String addressKey,
                             String displayAddress,
                             boolean manualSource) {
        if (TextUtils.isEmpty(addressKey) || sourceFile == null || !sourceFile.exists()) {
            return false;
        }
        File dest = new File(storageDir, "apt_" + System.currentTimeMillis() + ".jpg");
        try {
            copyFile(sourceFile, dest);
        } catch (IOException e) {
            FileLog.getInstance().error(TAG, "copy apartment photo failed", e);
            if (dest.exists()) dest.delete();
            return false;
        }
        ApartmentPhotoEntity existing = repository.findByKey(addressKey);
        ApartmentPhotoEntity entity = existing != null ? existing : new ApartmentPhotoEntity();
        entity.addressKey = addressKey;
        entity.displayAddress = displayAddress;
        entity.filePath = dest.getAbsolutePath();
        entity.savedAt = System.currentTimeMillis();
        entity.lastUsedAt = entity.savedAt;
        entity.source = manualSource ? ApartmentPhotoEntity.SOURCE_MANUAL : ApartmentPhotoEntity.SOURCE_AUTO;
        long id = repository.upsert(entity);
        if (id > 0) {
            entity.id = id;
        }
        if (existing != null && !TextUtils.isEmpty(existing.filePath)
                && !existing.filePath.equals(dest.getAbsolutePath())) {
            File old = new File(existing.filePath);
            if (old.exists()) old.delete();
        }
        return true;
    }

    public List<ApartmentPhotoEntity> listAll() {
        return repository.listAll();
    }

    public void delete(ApartmentPhotoEntity entity) {
        if (entity == null) return;
        repository.delete(entity.id);
        if (!TextUtils.isEmpty(entity.filePath)) {
            File file = new File(entity.filePath);
            if (file.exists()) file.delete();
        }
    }

    public void updateAddressKey(long id, String newKey, String displayAddress) {
        if (TextUtils.isEmpty(newKey)) return;
        ApartmentPhotoEntity entity = repository.findById(id);
        if (entity == null) return;
        entity.addressKey = newKey;
        entity.displayAddress = displayAddress;
        repository.update(entity);
    }

    public String suggestManualBase(@Nullable String address) {
        return ApartmentAddressKeyBuilder.normalizeManualInput(address);
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileChannel inChannel = new FileInputStream(source).getChannel();
             FileChannel outChannel = new FileOutputStream(dest).getChannel()) {
            long size = inChannel.size();
            long transferred = 0;
            while (transferred < size) {
                transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
            }
        }
    }

    public Set<String> getAutoFilePathsFor(List<ApartmentPhotoEntity> entities) {
        Set<String> paths = new HashSet<>();
        if (entities == null) return paths;
        for (ApartmentPhotoEntity entity : entities) {
            if (entity != null && !TextUtils.isEmpty(entity.filePath)) {
                paths.add(entity.filePath);
            }
        }
        return paths;
    }
}
