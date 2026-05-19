module FtmsMachineKind {
    const UNKNOWN = 0;
    const INDOOR_BIKE = 1;
    const TREADMILL = 2;
    const CROSS_TRAINER = 3;

    function label(kind) {
        if (kind == INDOOR_BIKE) {
            return "BIKE";
        }

        if (kind == TREADMILL) {
            return "TM";
        }

        if (kind == CROSS_TRAINER) {
            return "ELL";
        }

        return "FTMS";
    }
}
