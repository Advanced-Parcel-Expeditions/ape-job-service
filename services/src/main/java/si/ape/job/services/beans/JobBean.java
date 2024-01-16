package si.ape.job.services.beans;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


import si.ape.job.lib.Job;
import si.ape.job.lib.JobStatus;
import si.ape.job.lib.Parcel;
import si.ape.job.models.converters.JobConverter;
import si.ape.job.models.entities.*;
import si.ape.job.models.converters.ParcelConverter;
import si.ape.job.services.machine.JobStateMachine;


@RequestScoped
public class JobBean {

    private Logger log = Logger.getLogger(JobBean.class.getName());

    @Inject
    private EntityManager em;

    @Inject
    private JobStateMachine jobStateMachine;

    private final int JOB_STATUS_COMPLETED = 2;

    private final int JOB_STATUS_CANCELLED = 3;

    public List<Job> getJobsOfEmployee(Integer employeeId) {
        TypedQuery<JobEntity> query = em.createNamedQuery("JobEntity.getJobsOfEmployee", JobEntity.class);
        query.setParameter("employeeId", employeeId);
        List<Job> jobs = query.getResultList().stream().map(JobConverter::toDto).toList();
        return jobs;
    }

    public List<Job> getJobsOfEmployeeWithStatus(Integer employeeId, Integer status) {
        TypedQuery<JobEntity> query = em.createNamedQuery("JobEntity.getJobsOfEmployeeWithStatus", JobEntity.class);
        query.setParameter("employeeId", employeeId);
        query.setParameter("status", status);
        List<Job> jobs = query.getResultList().stream().map(JobConverter::toDto).toList();
        return jobs;
    }

    public Parcel viewParcelOfJob(Integer jobId) {
        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class);
        query.setParameter("jobId", jobId);
        ParcelEntity parcelEntity = query.getResultList().stream().findFirst().orElse(null);
        if (parcelEntity != null) {
            return ParcelConverter.toDto(parcelEntity);
        }
        return null;
    }

    public Job createJob(Job job, String parcelId) {
        try {
            beginTx();
            JobEntity jobEntity = new JobEntity();
            jobEntity.setDateCreated(Instant.now());
            JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);
            jobEntity.setJobStatus(jobStatusEntity);
            jobEntity.setJobType(em.find(JobTypeEntity.class, job.getJobType().getId()));
            jobEntity.setStaff(em.find(EmployeeEntity.class, job.getStaff().getId()));
            em.persist(jobEntity);
            commitTx();

            beginTx();
            ParcelEntity parcelEntity = em.find(ParcelEntity.class, parcelId);
            JobParcelEntity jobParcelEntity = new JobParcelEntity();
            jobParcelEntity.setJob(jobEntity);
            jobParcelEntity.setParcel(parcelEntity);
            em.persist(jobParcelEntity);
            commitTx();
            return JobConverter.toDto(jobEntity);
        } catch(Exception e){
            rollbackTx();
            e.printStackTrace();
            return null;
        }
    }

    public Job completeJob(Integer jobId) {
        JobEntity jobEntity = em.find(JobEntity.class, jobId);
        if (jobEntity != null) {
            beginTx();
            JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, JOB_STATUS_COMPLETED);
            log.info("Job ID: " + jobId + " Job status: " + jobStatusEntity + " Job entity: " + jobEntity);
            jobEntity.setJobStatus(jobStatusEntity);
            jobEntity.setDateCompleted(Instant.now());
            jobStateMachine.next(jobEntity);
            commitTx();
        }
        return JobConverter.toDto(jobEntity);
    }

    public Job cancelJob(Integer jobId) {
        JobEntity jobEntity = em.find(JobEntity.class, jobId);
        if (jobEntity != null) {
            beginTx();
            JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, JOB_STATUS_CANCELLED);
            jobEntity.setJobStatus(jobStatusEntity);
            commitTx();
        }
        return null;
    }

    public Job linkJobAndParcel(Integer jobId, String parcelId) {
        JobEntity jobEntity = em.find(JobEntity.class, jobId);
        if (jobEntity != null) {
            beginTx();
            ParcelEntity parcelEntity = em.find(ParcelEntity.class, parcelId);
            if (parcelEntity != null) {
                JobParcelEntity jobParcelEntity = new JobParcelEntity();
                jobParcelEntity.setJob(jobEntity);
                jobParcelEntity.setParcel(parcelEntity);
                em.persist(jobParcelEntity);
                commitTx();
                return JobConverter.toDto(jobEntity);
            } else {
                rollbackTx();
            }
        }
        return null;
    }

    private void beginTx() {
        if (!em.getTransaction().isActive()) {
            em.getTransaction().begin();
        }
    }

    private void commitTx() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().commit();
        }
    }

    private void rollbackTx() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
    }

}
