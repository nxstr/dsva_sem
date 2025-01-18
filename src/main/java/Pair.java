import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Pair {
    private int nodeId;
    private int value;

    public Pair(int nodeId, int value) {
        this.nodeId = nodeId;
        this.value = value;
    }
}
