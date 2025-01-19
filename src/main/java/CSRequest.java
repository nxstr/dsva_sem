import lombok.Getter;

@Getter
public class CSRequest implements Comparable<CSRequest> {

    private final int timestamp;
    private final int nodeId;

    public CSRequest(int timestamp, int nodeId) {
        this.timestamp = timestamp;
        this.nodeId = nodeId;
    }

    @Override
    public int compareTo(CSRequest other) {
        if (this.timestamp == other.timestamp) {
            return Integer.compare(this.nodeId, other.nodeId);
        }
        return Integer.compare(this.timestamp, other.timestamp);
    }

    public int getWinnerNodeId(CSRequest other) {
        if (this.timestamp == other.timestamp) {
            return Math.min(this.nodeId, other.nodeId);
        }
        return (this.timestamp < other.timestamp) ? this.nodeId : other.nodeId;
    }
}

