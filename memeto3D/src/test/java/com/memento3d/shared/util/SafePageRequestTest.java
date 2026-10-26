package com.memento3d.shared.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SafePageRequestTest {

    private static final Map<String, String> ALLOWED = Map.of(
            "createdAt", "createdAt",
            "totalAmount", "totalPrice"
    );

    private PageRequest build(int page, int size, String sort) {
        return SafePageRequest.of(page, size, sort, ALLOWED, "createdAt", Sort.Direction.DESC);
    }

    @Test
    void mapsWhitelistedApiNameToEntityProperty() {
        Sort.Order order = build(0, 10, "totalAmount,asc").getSort().getOrderFor("totalPrice");
        assertNotNull(order);
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void rejectsArbitraryFieldAndFallsBackToDefault() {
        PageRequest pr = build(0, 10, "password,asc; DROP TABLE users");
        assertNotNull(pr.getSort().getOrderFor("createdAt"));
        assertEquals(Sort.Direction.DESC, pr.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(null, pr.getSort().getOrderFor("password"));
    }

    @Test
    void defaultsToCreatedAtDescWhenSortMissing() {
        PageRequest pr = build(0, 10, null);
        assertEquals(Sort.Direction.DESC, pr.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void clampsPageSizeAndNegativePage() {
        assertEquals(SafePageRequest.MAX_PAGE_SIZE, build(0, 100000, null).getPageSize());
        assertEquals(1, build(0, 0, null).getPageSize());
        assertEquals(0, build(-5, 10, null).getPageNumber());
    }

    @Test
    void addsStableIdTiebreaker() {
        assertNotNull(build(0, 10, "createdAt,desc").getSort().getOrderFor("id"));
    }
}
