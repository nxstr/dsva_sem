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
        // If timestamps are the same, return the nodeId of the node with the smallest ID
        if (this.timestamp == other.timestamp) {
            return Integer.compare(this.nodeId, other.nodeId);
        }
        // Otherwise, return the nodeId of the node with the smaller timestamp
        return Integer.compare(this.timestamp, other.timestamp);
    }

    // Method to get the winner's nodeId
    public int getWinnerNodeId(CSRequest other) {
        // First compare timestamps
        if (this.timestamp == other.timestamp) {
            // If timestamps are equal, the node with the smaller nodeId wins
            return Math.min(this.nodeId, other.nodeId);
        }
        // If timestamps are different, the node with the smaller timestamp wins
        return (this.timestamp < other.timestamp) ? this.nodeId : other.nodeId;
    }
}

