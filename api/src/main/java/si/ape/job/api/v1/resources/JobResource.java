package si.ape.job.api.v1.resources;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import si.ape.job.api.v1.resources.requests.CreateJobRequest;
import si.ape.job.lib.connections.JobConnection;
import si.ape.job.lib.connections.ParcelConnection;
import si.ape.job.services.beans.JobBean;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.logging.Logger;

import si.ape.job.lib.*;

/**
 * GraphQL API for managing jobs. It contains end-points for viewing all jobs, pending jobs, completed jobs and an
 * end-point for marking a job as completed or creating a job. All of these end points work for a single user only.
 */
@GraphQLApi
@ApplicationScoped
public class JobResource {

    private final Logger log = Logger.getLogger(JobResource.class.getName());

    @Inject
    private JobBean jobBean;


    @Context
    protected UriInfo uriInfo;

    @Query
    public JobConnection getJobsOfEmployee(@Parameter(description = "Employee ID") @Name("employeeId") Integer employeeId) {
        List<Job> jobs = jobBean.getJobsOfEmployee(employeeId);
        return new JobConnection(jobs, jobs.size());
    }

    @Query
    public JobConnection getJobsOfEmployeeWithStatus(@Parameter(description = "Employee ID") @Name("employeeId") Integer employeeId,
    		@Parameter(description = "Job status") @Name("status") Integer status) {
        List<Job> jobs = jobBean.getJobsOfEmployeeWithStatus(employeeId, status);
        return new JobConnection(jobs, jobs.size());
    }

    @Query
    public ParcelConnection viewParcelOfJob(@Parameter(description = "Job ID") @Name("jobId") Integer jobId) {
        Parcel parcel = jobBean.viewParcelOfJob(jobId);
        if (parcel == null) {
            return new ParcelConnection(List.of(), 0);
        }
        return new ParcelConnection(List.of(parcel), 1);
    }

    @Mutation
    public JobConnection createJob(@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateJobRequest.class))) @Name("jobRequest") CreateJobRequest jobRequest) {
        Job job = jobBean.createJob(jobRequest.getJob(), jobRequest.getParcelId());
        return new JobConnection(List.of(job), 1);
    }

    @Mutation
    public JobConnection completeJob(@Parameter(description = "Job ID") @Name("jobId") Integer jobId) {
        Job job = jobBean.completeJob(jobId);
        return new JobConnection(List.of(job), 1);
    }

    @Mutation
    public JobConnection cancelJob(@Parameter(description = "Job ID") @Name("jobId") Integer jobId) {
        Job job = jobBean.cancelJob(jobId);
        return new JobConnection(List.of(job), 1);
    }

    @Mutation
    public JobConnection linkJobAndParcel(@Parameter(description = "Job ID") @Name("jobId") Integer jobId,
    		@Parameter(description = "Parcel ID") @Name("parcelId") String parcelId) {
        Job job = jobBean.linkJobAndParcel(jobId, parcelId);
        if (job == null) {
            return new JobConnection(List.of(), 0);
        }
        return new JobConnection(List.of(job), 1);
    }

}
