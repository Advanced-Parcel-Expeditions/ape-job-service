package si.ape.job.lib.connections;

import java.util.List;

public class ConnectionBase<T> {

    private final List<T> edges;

    private final long totalCount;

    public ConnectionBase(List<T> edges, long totalCount) {
        this.edges = edges;
        this.totalCount = totalCount;
    }

    public List<T> getEdges() {
        return edges;
    }

    public long getTotalCount() {
        return totalCount;
    }

}
