package com.kdab.charm;

class Task {
    public final long id;
    public final String name;
    public int seconds;
    public boolean active;

    public Task(long id, String name) {
        this.id = id;
        this.name = name;
        this.active = false;
        this.seconds = 0;
    }

    @Override
    public boolean equals(Object o) {
        Task other = o instanceof Task ? ((Task) o) : null;
        return (other != null) && (other.id == this.id);
    }

    @Override
    public String toString() {
        if (active)
            return String.format("%04d %s [%02d:%02d]", id, name, seconds / 60, seconds % 60);
        return String.format("%04d %s", id, name);
    }
}
