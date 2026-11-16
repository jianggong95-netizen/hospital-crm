package com.hospital.crm.patient360.support;

import com.hospital.crm.his.api.HisDtos;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HIS 患者360结果的短时进程内缓存。
 *
 * <p>缓存键为数据源系统与患者主索引；仅在 TTL 内复用上一次实时或降级结果，
 * 不缓存 HIS 查询失败，避免把“暂不可用”固化为空数据。缓存触顶时优先清理过期项。</p>
 */
@Component
public class Patient360Cache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Patient360Properties properties;

    public Patient360Cache(Patient360Properties properties) {
        this.properties = properties;
    }

    public Optional<HisDtos.Patient360> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.data());
    }

    public void put(String key, HisDtos.Patient360 data) {
        if (properties.getCacheTtlSeconds() <= 0 || data == null) {
            return;
        }
        evictIfFull();
        entries.put(key, new Entry(data, Instant.now()));
    }

    public void clear() {
        entries.clear();
    }

    private boolean isExpired(Entry entry) {
        return !Instant.now().isBefore(entry.storedAt().plusSeconds(properties.getCacheTtlSeconds()));
    }

    private void evictIfFull() {
        int maxEntries = Math.max(properties.getCacheMaxEntries(), 1);
        if (entries.size() < maxEntries) {
            return;
        }
        entries.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        if (entries.size() >= maxEntries) {
            entries.keySet().stream().findFirst().ifPresent(entries::remove);
        }
    }

    private record Entry(HisDtos.Patient360 data, Instant storedAt) {
    }
}
