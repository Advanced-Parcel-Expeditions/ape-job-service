package si.ape.job.models.converters;

import si.ape.job.models.entities.BranchEntity;
import si.ape.job.lib.Branch;

public class BranchConverter {

    public static Branch toDto(BranchEntity entity) {

        Branch dto = new Branch();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setBranchType(BranchTypeConverter.toDto(entity.getBranchType()));
        dto.setStreet(StreetConverter.toDto(entity.getStreet()));
        return dto;

    }

    public static BranchEntity toEntity(Branch dto) {

        BranchEntity entity = new BranchEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setBranchType(BranchTypeConverter.toEntity(dto.getBranchType()));
        entity.setStreet(StreetConverter.toEntity(dto.getStreet()));
        return entity;

    }

}
