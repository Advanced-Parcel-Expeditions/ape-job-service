package si.ape.job.lib.connections;

import si.ape.job.lib.Job;

import java.util.List;

public class JobConnection extends ConnectionBase<Job> {

    public JobConnection(List<Job> edges, long totalCount) {
        super(edges, totalCount);
    }

}
