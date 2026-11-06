package com.memento3d.customization.api.response;

public record CatalogImportResponse(String code,String status,int assetCount,int verifiedObjects,boolean unchanged) {}
