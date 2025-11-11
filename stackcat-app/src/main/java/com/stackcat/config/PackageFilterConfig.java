package com.stackcat.config;

import com.stackcat.util.PackageFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PackageFilterConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "stackcat.filter.packages")
    public PackageFilterProperties packageFilterProperties() {
        return new PackageFilterProperties();
    }

    @Bean
    public PackageFilter packageFilter(PackageFilterProperties properties) {
        return new PackageFilter(properties.getExclude(), properties.getInclude());
    }

    public static class PackageFilterProperties {
        private List<String> exclude = List.of();
        private List<String> include = List.of();

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }

        public List<String> getInclude() {
            return include;
        }

        public void setInclude(List<String> include) {
            this.include = include;
        }
    }
}

