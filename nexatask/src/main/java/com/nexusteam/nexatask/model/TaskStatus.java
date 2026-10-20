package com.nexusteam.nexatask.model;

/** Public lifecycle status of a {@link com.nexusteam.nexatask.NexaTask}. */
public enum TaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    CANCELLED,
    FINISHED,
    TIMEOUT,
    ERROR
}
