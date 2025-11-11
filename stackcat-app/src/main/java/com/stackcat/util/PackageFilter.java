package com.stackcat.util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PackageFilter {
    private final Set<String> excludePackages;
    private final Set<String> includePackages;
    private final boolean hasIncludeFilter;

    public PackageFilter(List<String> excludePackages, List<String> includePackages) {
        this.excludePackages = excludePackages != null ? 
            excludePackages.stream().collect(Collectors.toSet()) : 
            Set.of();
        this.includePackages = includePackages != null ? 
            includePackages.stream().collect(Collectors.toSet()) : 
            Set.of();
        this.hasIncludeFilter = !this.includePackages.isEmpty();
    }

    public boolean shouldTrack(String className) {
        if (className == null) {
            return false;
        }

        // If include filter is set, only track classes in included packages
        if (hasIncludeFilter) {
            return includePackages.stream().anyMatch(className::startsWith);
        }

        // If no include filter, check exclude filter
        if (!excludePackages.isEmpty()) {
            return excludePackages.stream().noneMatch(className::startsWith);
        }

        // No filters, track everything
        return true;
    }
}

