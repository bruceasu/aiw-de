package me.asu.aidev.work.domain;

public record TaskPriority(int value) implements Comparable<TaskPriority> {
    public static final TaskPriority LOW = new TaskPriority(25);
    public static final TaskPriority NORMAL = new TaskPriority(50);
    public static final TaskPriority HIGH = new TaskPriority(75);
    public static final TaskPriority CRITICAL = new TaskPriority(100);

    public TaskPriority {
        if (value < 0 || value > 100) throw new IllegalArgumentException("priority must be between 0 and 100");
    }

    @Override
    public int compareTo(TaskPriority other) {
        return Integer.compare(value, other.value);
    }
}
