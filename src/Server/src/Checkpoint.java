import java.time.Instant;

/**
 * Checkpoint class
 * Created by PD on 22.04.2017.
 */
class Checkpoint {

    private Task task;
    private double[] location;
    private Instant time_created;
    private Instant time_visited;
    private Instant time_completed;
    private boolean visited = false;
    private boolean completed = false;
    private boolean correct = false;

    Checkpoint(double[] location, String description) {
        this.location = location;
        task = new Task(description);
        time_created = Instant.now();
    }

    double[] getLocation() {
        return location;
    }

    Task getTask() {
        return task;
    }

    Instant getTime_created() {
        return time_created;
    }

    void setTime_visited(Instant time_visited) {
        this.time_visited = time_visited;
    }

    Instant getTime_visited() {
        return time_visited;
    }

    Instant getTime_completed() {
        return time_completed;
    }

    void setTime_completed(Instant time_completed) {
        this.time_completed = time_completed;
    }

    boolean isVisited() {
        return visited;
    }

    void setVisited() {
        this.visited = true;
    }

    boolean isCompleted() {
        return completed;
    }

    void setCompleted() {
        this.completed = true;
    }

    boolean isCorrect() {
        return correct;
    }

    void setCorrect() {
        this.correct = true;
    }

}
