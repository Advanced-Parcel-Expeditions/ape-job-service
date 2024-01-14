package si.ape.job.services.machine;

import org.json.JSONObject;
import si.ape.job.lib.*;
import si.ape.job.models.converters.BranchConverter;
import si.ape.job.models.converters.BranchTypeConverter;
import si.ape.job.models.converters.StreetConverter;
import si.ape.job.models.entities.*;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class JobStateMachine {

    private final Logger log = Logger.getLogger(JobStateMachine.class.getName());

    @Inject
    private EntityManager em;

    public JobStateMachine() {
    }

    public void next(JobEntity jobEntity) {
        switch (getRoleOfEmployee(jobEntity)) {
            case WAREHOUSE_AGENT -> {
                if (isJobType(jobEntity, JobType.CHECK_IN)) {
                    log.info("Moving job " + jobEntity.getId() + " from warehouse agent to warehouse agent.");
                    moveJob(MoveType.WAREHOUSE_AGENT_TO_WAREHOUSE_AGENT, jobEntity);
                } else if (isAtFinalParcelCenter(jobEntity)) {
                    log.info("Moving job " + jobEntity.getId() + " from warehouse agent to delivery driver.");
                    moveJob(MoveType.WAREHOUSE_AGENT_TO_DELIVERY_DRIVER, jobEntity);
                } else {
                    log.info("Moving job " + jobEntity.getId() + " from warehouse agent to international driver.");
                    moveJob(MoveType.WAREHOUSE_AGENT_TO_INTERNATIONAL_DRIVER, jobEntity);
                }
            }
            case DELIVERY_DRIVER -> {
                if (isJobType(jobEntity, JobType.DELIVERY_CARGO_CONFIRMATION)) {
                    log.info("Moving job " + jobEntity.getId() + " from delivery driver to delivery driver.");
                    moveJob(MoveType.DELIVERY_DRIVER_TO_DELIVERY_DRIVER, jobEntity);
                } else if (isJobType(jobEntity, JobType.PARCEL_HANDOVER)) {
                    // The delivery is done.
                    log.info("Delivery is done.");
                } else {
                    log.info("Moving job " + jobEntity.getId() + " from delivery driver to warehouse agent.");
                    moveJob(MoveType.DELIVERY_DRIVER_TO_WAREHOUSE_AGENT, jobEntity);
                }
            }
            case INTERNATIONAL_DRIVER -> {
                if (isJobType(jobEntity, JobType.CARGO_DEPARTING_CONFIRMATION)) {
                    log.info("Moving job " + jobEntity.getId() + " from international driver to international driver.");
                    moveJob(MoveType.INTERNATIONAL_DRIVER_TO_INTERNATIONAL_DRIVER, jobEntity);
                } else {
                    log.info("Moving job " + jobEntity.getId() + " from international driver to warehouse agent.");
                    moveJob(MoveType.INTERNATIONAL_DRIVER_TO_WAREHOUSE_AGENT, jobEntity);
                }
            }
            case ORDER_CONFIRMATION_SPECIALIST -> {
                log.info("Moving job " + jobEntity.getId() + " from order confirmation specialist to delivery driver.");
                moveJob(MoveType.ORDER_CONFIRMATION_SPECIALIST_TO_DELIVERY_DRIVER, jobEntity);
            }
            default -> {

            }
        }
    }

    private void moveJob(MoveType moveType, JobEntity jobEntity) {
        switch (moveType) {
            case ORDER_CONFIRMATION_SPECIALIST_TO_DELIVERY_DRIVER -> {
                moveFromOrderConfirmationSpecialistToDeliveryDriver(jobEntity);
            }
            case DELIVERY_DRIVER_TO_WAREHOUSE_AGENT -> {
                moveFromDeliveryDriverToWarehouseAgent(jobEntity);
            }
            case WAREHOUSE_AGENT_TO_WAREHOUSE_AGENT -> {
                moveFromWarehouseAgentToWarehouseAgent(jobEntity);
            }
            case WAREHOUSE_AGENT_TO_INTERNATIONAL_DRIVER -> {
                moveFromWarehouseAgentToInternationalDriver(jobEntity);
            }
            case INTERNATIONAL_DRIVER_TO_INTERNATIONAL_DRIVER -> {
                moveFromInternationalDriverToInternationalDriver(jobEntity);
            }
            case INTERNATIONAL_DRIVER_TO_WAREHOUSE_AGENT -> {
                moveFromInternationalDriverToWarehouseAgent(jobEntity);
            }
            case WAREHOUSE_AGENT_TO_DELIVERY_DRIVER -> {
                moveFromWarehouseAgentToDeliveryDriver(jobEntity);
            }
            case DELIVERY_DRIVER_TO_DELIVERY_DRIVER -> {
                moveFromDeliveryDriverToDeliveryDriver(jobEntity);
            }
            default -> {

            }
        }
    }

    private void moveFromOrderConfirmationSpecialistToDeliveryDriver(JobEntity jobEntity) {
        StreetEntity streetEntity = jobEntity.getStaff().getBranch().getStreet();
        log.info("Here is the street: " + streetEntity);
        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcelEntity = query.getSingleResult();
        log.info("Here is the parcel: " + parcelEntity);

        // Find branches in which the parcel has already been.
        TypedQuery<JobParcelEntity> jobParcelQuery = em.createNamedQuery("JobParcelEntity.getByParcelId", JobParcelEntity.class)
                .setParameter("parcelId", parcelEntity.getId());
        try {
            List<JobParcelEntity> jobParcelEntities = jobParcelQuery.getResultList();
            List<BranchEntity> branchEntities = jobParcelEntities.stream().map(JobParcelEntity::getJob).map(JobEntity::getStaff).map(EmployeeEntity::getBranch).toList();
            List<Branch> branches = branchEntities.stream().map(BranchConverter::toDto).toList();

            Branch nextHopBranch = getNextHopExclude(streetEntity, parcelEntity.getRecipientStreet(), branches);
            List<EmployeeEntity> deliveryDrivers = em.createNamedQuery("EmployeeEntity.getAllAtBranchWithRole", EmployeeEntity.class)
                    .setParameter("branchId", nextHopBranch.getId())
                    .setParameter("roleId", 4)
                    .getResultList();
            EmployeeEntity deliveryDriver = deliveryDrivers.get(new Random().nextInt(deliveryDrivers.size()));

            JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 2);
            JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

            log.info("Delivery driver job status: " + jobStatusEntity);

            JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, deliveryDriver);

            // Link the new job to the same parcel as the old job.
            linkJobToParcel(newJobEntity, parcelEntity.getId());
        } catch (Exception e) {
            log.severe("Exception: " + e.getMessage());
        }
    }

    private void moveFromDeliveryDriverToWarehouseAgent(JobEntity jobEntity) {
        BranchEntity branch = jobEntity.getStaff().getBranch();
        List<EmployeeEntity> warehouseAgents = em.createNamedQuery("EmployeeEntity.getAllAtBranchWithRole", EmployeeEntity.class)
                .setParameter("branchId", branch.getId())
                .setParameter("roleId", 3)
                .getResultList();
        EmployeeEntity warehouseAgent = warehouseAgents.get(new Random().nextInt(warehouseAgents.size()));

        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 3);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, warehouseAgent);

        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    private void moveFromWarehouseAgentToWarehouseAgent(JobEntity jobEntity) {
        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 4);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, jobEntity.getStaff());

        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    private void moveFromWarehouseAgentToInternationalDriver(JobEntity jobEntity) {
        BranchEntity branch = jobEntity.getStaff().getBranch();
        String countryCode = branch.getStreet().getCity().getCountry().getCode();
        BranchEntity countryHQ = em.createNamedQuery("BranchEntity.getCountryHQ", BranchEntity.class)
                .setParameter("countryCode", countryCode)
                .getSingleResult();
        List<EmployeeEntity> internationalDrivers = em.createNamedQuery("EmployeeEntity.getAllAtBranchWithRole", EmployeeEntity.class)
                .setParameter("branchId", countryHQ.getId())
                .setParameter("roleId", 5)
                .getResultList();
        EmployeeEntity internationalDriver = internationalDrivers.get(new Random().nextInt(internationalDrivers.size()));

        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 5);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, internationalDriver);

        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    private void moveFromInternationalDriverToInternationalDriver(JobEntity jobEntity) {
        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 6);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, jobEntity.getStaff());

        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    private void moveFromInternationalDriverToWarehouseAgent(JobEntity jobEntity) {
        BranchEntity branch = jobEntity.getStaff().getBranch();
        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        StreetEntity staffStreet = jobEntity.getStaff().getBranch().getStreet();

        // Find branches in which the parcel has already been.
        TypedQuery<JobParcelEntity> jobParcelQuery = em.createNamedQuery("JobParcelEntity.getByParcelId", JobParcelEntity.class)
                .setParameter("parcelId", parcel.getId());
        List<JobParcelEntity> jobParcelEntities = jobParcelQuery.getResultList();
        List<BranchEntity> branchEntities = jobParcelEntities.stream().map(JobParcelEntity::getJob).map(JobEntity::getStaff).map(EmployeeEntity::getBranch).toList();
        List<Branch> branches = branchEntities.stream().map(BranchConverter::toDto).toList();

        Branch nextHopBranch = getNextHopExclude(branch.getStreet(), parcel.getRecipientStreet(), branches);

        List<EmployeeEntity> warehouseAgents = em.createNamedQuery("EmployeeEntity.getAllAtBranchWithRole", EmployeeEntity.class)
                .setParameter("branchId", nextHopBranch.getId())
                .setParameter("roleId", 3)
                .getResultList();
        EmployeeEntity warehouseAgent = warehouseAgents.get(new Random().nextInt(warehouseAgents.size()));

        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 3);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, warehouseAgent);

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    private void moveFromWarehouseAgentToDeliveryDriver(JobEntity jobEntity) {
        int branchId = jobEntity.getStaff().getBranch().getId();
        List<EmployeeEntity> deliveryDrivers = em.createNamedQuery("EmployeeEntity.getAllAtBranchWithRole", EmployeeEntity.class)
                .setParameter("branchId", branchId)
                .setParameter("roleId", 4)
                .getResultList();

        if (!deliveryDrivers.isEmpty()) {
            // Choose a delivery driver at random.
            EmployeeEntity deliveryDriver = deliveryDrivers.get(new Random().nextInt(deliveryDrivers.size()));

            JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 7);
            JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

            JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, deliveryDriver);

            TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                    .setParameter("jobId", jobEntity.getId());
            ParcelEntity parcel = query.getSingleResult();

            // Link the new job to the same parcel as the old job.
            linkJobToParcel(newJobEntity, parcel.getId());
        }
    }

    private void moveFromDeliveryDriverToDeliveryDriver(JobEntity jobEntity) {
        JobTypeEntity jobTypeEntity = em.find(JobTypeEntity.class, 8);
        JobStatusEntity jobStatusEntity = em.find(JobStatusEntity.class, 1);

        JobEntity newJobEntity = createJob(jobTypeEntity, jobStatusEntity, jobEntity.getStaff());

        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcel = query.getSingleResult();

        // Link the new job to the same parcel as the old job.
        linkJobToParcel(newJobEntity, parcel.getId());
    }

    // UTILITY METHODS

    private JobEntity createJob(JobTypeEntity jobTypeEntity, JobStatusEntity jobStatusEntity, EmployeeEntity employeeEntity) {
        JobEntity jobEntity = new JobEntity();
        jobEntity.setDateCreated(Instant.now());
        jobEntity.setJobType(jobTypeEntity);
        jobEntity.setJobStatus(jobStatusEntity);
        jobEntity.setStaff(employeeEntity);
        em.persist(jobEntity);
        return jobEntity;
    }

    private void linkJobToParcel(JobEntity jobEntity, String parcelId) {
        ParcelEntity parcelEntity = em.find(ParcelEntity.class, parcelId);
        JobParcelEntity jobParcelEntity = new JobParcelEntity();
        jobParcelEntity.setJob(jobEntity);
        jobParcelEntity.setParcel(parcelEntity);
        em.persist(jobParcelEntity);
    }

    /**
     * Performs a POST request to the routing service to get the next hop. The request consists of two `Street` DTOs.
     * The response is a `Branch` DTO.
     *
     * @param source
     * @param destination
     * @return
     */
    private Branch getNextHop(StreetEntity source, StreetEntity destination) {

        // Display source and destination streets.
        log.info("Source street: " + source);
        log.info("Source street name: " + source.getStreetName());
        log.info("Source street number: " + source.getStreetNumber());
        log.info("Source city name: " + source.getCity().getName());
        log.info("Source city code: " + source.getCity().getCode());
        log.info("Source country name: " + source.getCity().getCountry().getName());
        log.info("Source country code: " + source.getCity().getCountry().getCode());

        log.info("Destination street: " + destination);
        log.info("Destination street name: " + destination.getStreetName());
        log.info("Destination street number: " + destination.getStreetNumber());
        log.info("Destination city name: " + destination.getCity().getName());
        log.info("Destination city code: " + destination.getCity().getCode());
        log.info("Destination country name: " + destination.getCity().getCountry().getName());
        log.info("Destination country code: " + destination.getCity().getCountry().getCode());

        String apiUrl = "http://dev.okeanos.mywire.org/routing/v1/routing/next-hop";
        Client client = ClientBuilder.newClient();
        NextHopRequest nextHopRequest = new NextHopRequest();
        nextHopRequest.setSource(StreetConverter.toDto(source));
        nextHopRequest.setDestination(StreetConverter.toDto(destination));
        Response response = client.target(apiUrl)
                .request()
                .post(Entity.json(nextHopRequest), Response.class);
        log.info("Response status: " + response.getStatus());
        if (response.getStatus() == 200) {
            JSONObject json = new JSONObject(response.readEntity(String.class));
            log.info(json.toString());
            if (json.has("branch")) {
                Branch branch = new Branch();
                branch.setId(json.getJSONObject("branch").getInt("id"));
                branch.setName(json.getJSONObject("branch").getString("name"));
                BranchTypeEntity branchType = em.find(BranchTypeEntity.class, json.getJSONObject("branch").getJSONObject("branchType").getInt("id"));
                branch.setBranchType(BranchTypeConverter.toDto(branchType));
                // Extract the street from the branch.
                Street street = new Street();
                street.setStreetName(json.getJSONObject("branch").getJSONObject("street").getString("streetName"));
                street.setStreetNumber(json.getJSONObject("branch").getJSONObject("street").getInt("streetNumber"));
                City city = new City();
                city.setName(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getString("name"));
                city.setCode(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getString("code"));
                Country country = new Country();
                country.setName(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getJSONObject("country").getString("name"));
                country.setCode(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getJSONObject("country").getString("code"));
                city.setCountry(country);
                street.setCity(city);
                branch.setStreet(street);
                return branch;
            }
        }
        return null;
    }

    private Branch getNextHopExclude(StreetEntity source, StreetEntity destination, List<Branch> exclude) {
        String apiUrl = "http://dev.okeanos.mywire.org/routing/v1/routing/next-hop-exclude";
        Client client = ClientBuilder.newClient();

        // Print excluded branch id's.
        for (Branch branch : exclude) {
            log.info("Excluded branch id: " + branch.getId());
        }

        NextHopExcludeRequest nextHopRequest = new NextHopExcludeRequest();
        nextHopRequest.setSource(StreetConverter.toDto(source));
        nextHopRequest.setDestination(StreetConverter.toDto(destination));
        nextHopRequest.setExclude(exclude);
        Response response = client.target(apiUrl)
                .request()
                .post(Entity.json(nextHopRequest), Response.class);

        if (response.getStatus() == 200) {
            JSONObject json = new JSONObject(response.readEntity(String.class));
            log.info(json.toString());
            if (json.has("branch")) {
                Branch branch = new Branch();
                branch.setId(json.getJSONObject("branch").getInt("id"));
                branch.setName(json.getJSONObject("branch").getString("name"));
                BranchTypeEntity branchType = em.find(BranchTypeEntity.class, json.getJSONObject("branch").getJSONObject("branchType").getInt("id"));
                branch.setBranchType(BranchTypeConverter.toDto(branchType));
                // Extract the street from the branch.
                Street street = new Street();
                street.setStreetName(json.getJSONObject("branch").getJSONObject("street").getString("streetName"));
                street.setStreetNumber(json.getJSONObject("branch").getJSONObject("street").getInt("streetNumber"));
                City city = new City();
                city.setName(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getString("name"));
                city.setCode(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getString("code"));
                Country country = new Country();
                country.setName(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getJSONObject("country").getString("name"));
                country.setCode(json.getJSONObject("branch").getJSONObject("street").getJSONObject("city").getJSONObject("country").getString("code"));
                city.setCountry(country);
                street.setCity(city);
                branch.setStreet(street);
                return branch;
            }
        }
        return null;
    }

    private boolean isAtFinalParcelCenter(JobEntity jobEntity) {
        BranchEntity branch = jobEntity.getStaff().getBranch();
        TypedQuery<ParcelEntity> query = em.createNamedQuery("JobParcelEntity.getParcelByJobId", ParcelEntity.class)
                .setParameter("jobId", jobEntity.getId());
        ParcelEntity parcelEntity = query.getSingleResult();
        CustomerEntity recipient = parcelEntity.getRecipient();
        String branchCountryCode = branch.getStreet().getCity().getCountry().getCode();
        String recipientCountryCode = recipient.getStreet().getCity().getCountry().getCode();
        if (branchCountryCode.equals(recipientCountryCode)) {
            return true;
        }
        return false;
    }

    private boolean isJobType(JobEntity jobEntity, JobType jobType) {
        return switch (jobEntity.getJobType().getId()) {
            case 1 -> jobType == JobType.ORDER_PROCESSING;
            case 2 -> jobType == JobType.HANDOVER;
            case 3 -> jobType == JobType.CHECK_IN;
            case 4 -> jobType == JobType.CHECK_OUT;
            case 5 -> jobType == JobType.CARGO_DEPARTING_CONFIRMATION;
            case 6 -> jobType == JobType.CARGO_ARRIVAL_CONFIRMATION;
            case 7 -> jobType == JobType.DELIVERY_CARGO_CONFIRMATION;
            case 8 -> jobType == JobType.PARCEL_HANDOVER;
            default -> false;
        };
    }

    private StaffRole getRoleOfEmployee(JobEntity jobEntity) {
        int roleId = jobEntity.getStaff().getUser().getRole().getId();
        return switch (roleId) {
            case 1 -> StaffRole.ADMINISTRATOR;
            case 2 -> StaffRole.WAREHOUSE_MANAGER;
            case 3 -> StaffRole.WAREHOUSE_AGENT;
            case 4 -> StaffRole.DELIVERY_DRIVER;
            case 5 -> StaffRole.INTERNATIONAL_DRIVER;
            case 6 -> StaffRole.LOGISTICS_AGENT;
            case 7 -> StaffRole.ORDER_CONFIRMATION_SPECIALIST;
            default -> null;
        };
    }

    /**
     * The NextHopRequest class represents a data-transfer object for the next hop request. It acts as a wrapper for the
     * source and destination streets.
     */
    public class NextHopRequest {

        /**
         * The source street.
         */
        private Street source;

        /**
         * The destination street.
         */
        private Street destination;

        /**
         * Gets the source street.
         *
         * @return the source street
         */
        public Street getSource() {
            return source;
        }

        /**
         * Sets the source street.
         *
         * @param source the source street
         */
        public void setSource(Street source) {
            this.source = source;
        }

        /**
         * Gets the destination street.
         *
         * @return the destination street
         */
        public Street getDestination() {
            return destination;
        }

        /**
         * Sets the destination street.
         *
         * @param destination the destination street
         */
        public void setDestination(Street destination) {
            this.destination = destination;
        }

    }
    public class NextHopExcludeRequest {

        /**
         * The source street.
         */
        private Street source;

        /**
         * The destination street.
         */
        private Street destination;

        /**
         * The exclude branches.
         */
        private List<Branch> exclude;

        /**
         * Gets the source street.
         *
         * @return the source street
         */
        public Street getSource() {
            return source;
        }

        /**
         * Sets the source street.
         *
         * @param source the source street
         */
        public void setSource(Street source) {
            this.source = source;
        }

        /**
         * Gets the destination street.
         *
         * @return the destination street
         */
        public Street getDestination() {
            return destination;
        }

        /**
         * Sets the destination street.
         *
         * @param destination the destination street
         */
        public void setDestination(Street destination) {
            this.destination = destination;
        }

        /**
         * Gets the exclude branches.
         *
         * @return the exclude branches
         */
        public List<Branch> getExclude() {
            return exclude;
        }

        /**
         * Sets the exclude branches.
         *
         * @param exclude the exclude branches
         */
        public void setExclude(List<Branch> exclude) {
            this.exclude = exclude;
        }

    }

}
