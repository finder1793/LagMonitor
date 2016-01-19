package com.github.games647.lagmonitor;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import java.util.Map;

public class Timing implements Comparable<Timing> {

    private final String category;

    private long totalTime;
    private long totalCount;

    private Map<String, Timing> subcategories = null;

    public Timing(String category) {
        this.category = category;

        totalTime = -1;
        totalCount = -1;
    }

    public Timing(String category, long totalTime, long count) {
        this.category = category;
        this.totalTime = totalTime;
        this.totalCount = count;
    }

    public String getCategoryName() {
        return category;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void addTotal(long total) {
        this.totalTime += total;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void addCount(long count) {
        this.totalCount += count;
    }

    public Map<String, Timing> getSubcategories() {
        return subcategories;
    }

    public void addSubcategory(String name, long totalTime, long count) {
        if (subcategories == null) {
            //lazy creating
            subcategories = Maps.newHashMap();
        }

        Timing timing = subcategories.get(name);
        if (timing == null) {
            subcategories.put(name, new Timing(name, totalTime, count));
        } else {
            timing.addTotal(totalTime);
            timing.addCount(totalTime);
        }
    }

    @Override
    public int compareTo(Timing other) {
        return Long.compare(totalTime, other.totalTime);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("category", category)
                .add("totalTime", totalCount)
                .add("count", totalCount)
                .toString();
    }
}
