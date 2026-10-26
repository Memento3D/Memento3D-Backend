package com.memento3d.shared.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Builds a safe {@link PageRequest} from untrusted client paging parameters.
 *
 * <ul>
 *   <li>Only whitelisted API sort names are accepted; they are mapped to entity properties,
 *       so clients can never pass arbitrary property/column names.</li>
 *   <li>Unknown sort names fall back to the default sort instead of failing with a 500.</li>
 *   <li>Page size is clamped to [1, {@value #MAX_PAGE_SIZE}].</li>
 * </ul>
 */
@Slf4j
public final class SafePageRequest {

    public static final int MAX_PAGE_SIZE = 100;

    private SafePageRequest() {
    }

    /**
     * @param sortParam     raw "field,direction" value from the client (may be null)
     * @param allowedFields API sort name -> entity property name
     * @param defaultField  entity property used when sort is missing or not allowed
     * @param defaultDir    direction used when sort is missing or not allowed
     */
    public static PageRequest of(int page, int size, String sortParam,
                                 Map<String, String> allowedFields,
                                 String defaultField, Sort.Direction defaultDir) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        String field = defaultField;
        Sort.Direction direction = defaultDir;

        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String requested = parts[0].trim();
            String mapped = allowedFields.get(requested);
            if (mapped != null) {
                field = mapped;
                if (parts.length > 1) {
                    direction = "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
                }
            } else {
                log.warn("Rejected non-whitelisted sort field '{}', using default '{}'", requested, defaultField);
            }
        }

        // Secondary id sort keeps pagination stable when the primary key has ties
        Sort sort = Sort.by(direction, field);
        if (!"id".equals(field)) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(safePage, safeSize, sort);
    }
}
