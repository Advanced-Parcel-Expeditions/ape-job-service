package si.ape.job.lib.connections;

import si.ape.job.lib.Parcel;

import java.util.List;

public class ParcelConnection extends ConnectionBase<Parcel> {

    public ParcelConnection(List<Parcel> edges, long totalCount) {
        super(edges, totalCount);
    }

}
