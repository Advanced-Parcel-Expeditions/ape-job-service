package si.ape.job.api.v1.resources.requests;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import si.ape.job.lib.Job;

public class CreateJobRequest {

    @Schema(required = true)
    private Job job;

    @Schema(required = true)
    private String parcelId;

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

}
