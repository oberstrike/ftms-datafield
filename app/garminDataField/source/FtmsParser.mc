using Toybox.Lang as Lang;

module FtmsParser {

    function parseIndoorBike(bytes) {
        var s = new FtmsSample(FtmsMachineKind.INDOOR_BIKE);

        if (!canRead(bytes, 0, 2)) { return s; }

        var flags = u16(bytes, 0);
        var p = 2;
        s.rawFlags = flags;

        // Bit 0 = More Data. If NOT set, instantaneous speed is present.
        if (!hasFlag(flags, 0)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.speedKmh = u16(bytes, p) / 100.0;
            p += 2;
        }

        // Bit 1: Average Speed, uint16, 0.01 km/h
        if (hasFlag(flags, 1)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 2: Instantaneous Cadence, uint16, 0.5 rpm
        if (hasFlag(flags, 2)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.cadenceRpm = u16(bytes, p) / 2.0;
            p += 2;
        }

        // Bit 3: Average Cadence, uint16, 0.5 rpm
        if (hasFlag(flags, 3)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 4: Total Distance, uint24, meters
        if (hasFlag(flags, 4)) {
            if (!canRead(bytes, p, 3)) { return s; }
            s.distanceM = u24(bytes, p).toFloat();
            p += 3;
        }

        // Bit 5: Resistance Level, sint16, unitless
        if (hasFlag(flags, 5)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.resistance = s16(bytes, p);
            p += 2;
        }

        // Bit 6: Instantaneous Power, sint16, watts
        if (hasFlag(flags, 6)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.powerW = s16(bytes, p);
            p += 2;
        }

        // Bit 7: Average Power, sint16, watts
        if (hasFlag(flags, 7)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 8: Total Energy uint16 + Energy/hour uint16 + Energy/min uint8
        if (hasFlag(flags, 8)) {
            if (!canRead(bytes, p, 5)) { return s; }
            p += 5;
        }

        // Bit 9: Heart Rate, uint8, bpm
        if (hasFlag(flags, 9)) {
            if (!canRead(bytes, p, 1)) { return s; }
            s.heartRateBpm = u8(bytes, p);
            p += 1;
        }

        // Bit 10: MET, uint8, 0.1 MET
        if (hasFlag(flags, 10)) {
            if (!canRead(bytes, p, 1)) { return s; }
            p += 1;
        }

        // Bit 11: Elapsed Time, uint16, seconds
        if (hasFlag(flags, 11)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.elapsedS = u16(bytes, p);
            p += 2;
        }

        // Bit 12: Remaining Time, uint16, seconds
        if (hasFlag(flags, 12)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.remainingS = u16(bytes, p);
            p += 2;
        }

        return s;
    }

    function parseTreadmill(bytes) {
        var s = new FtmsSample(FtmsMachineKind.TREADMILL);

        if (!canRead(bytes, 0, 2)) { return s; }

        var flags = u16(bytes, 0);
        var p = 2;
        s.rawFlags = flags;

        // Bit 0 = More Data. If NOT set, instantaneous speed is present.
        if (!hasFlag(flags, 0)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.speedKmh = u16(bytes, p) / 100.0;
            p += 2;
        }

        // Bit 1: Average Speed, uint16, 0.01 km/h
        if (hasFlag(flags, 1)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 2: Total Distance, uint24, meters
        if (hasFlag(flags, 2)) {
            if (!canRead(bytes, p, 3)) { return s; }
            s.distanceM = u24(bytes, p).toFloat();
            p += 3;
        }

        // Bit 3: Inclination + Ramp Angle Setting, sint16, 0.1 units
        if (hasFlag(flags, 3)) {
            if (!canRead(bytes, p, 4)) { return s; }
            s.inclinePct = s16(bytes, p) / 10.0;
            p += 2;
            s.rampDeg = s16(bytes, p) / 10.0;
            p += 2;
        }

        // Bit 4: Positive + Negative Elevation Gain, uint16, 0.1 meter
        if (hasFlag(flags, 4)) {
            if (!canRead(bytes, p, 4)) { return s; }
            s.positiveElevationM = u16(bytes, p) / 10.0;
            p += 2;
            s.negativeElevationM = u16(bytes, p) / 10.0;
            p += 2;
            s.elevationSource = 1;
        }

        // Bit 5: Instantaneous Pace, uint16
        if (hasFlag(flags, 5)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 6: Average Pace, uint16
        if (hasFlag(flags, 6)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 7: Expended Energy: total uint16 + per hour uint16 + per minute uint8
        if (hasFlag(flags, 7)) {
            if (!canRead(bytes, p, 5)) { return s; }
            p += 5;
        }

        // Bit 8: Heart Rate, uint8, bpm
        if (hasFlag(flags, 8)) {
            if (!canRead(bytes, p, 1)) { return s; }
            s.heartRateBpm = u8(bytes, p);
            p += 1;
        }

        // Bit 9: MET, uint8, 0.1 MET
        if (hasFlag(flags, 9)) {
            if (!canRead(bytes, p, 1)) { return s; }
            p += 1;
        }

        // Bit 10: Elapsed Time, uint16, seconds
        if (hasFlag(flags, 10)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.elapsedS = u16(bytes, p);
            p += 2;
        }

        // Bit 11: Remaining Time, uint16, seconds
        if (hasFlag(flags, 11)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.remainingS = u16(bytes, p);
            p += 2;
        }

        // Bit 12: Force On Belt + Power Output, sint16 + sint16
        if (hasFlag(flags, 12)) {
            if (!canRead(bytes, p, 4)) { return s; }
            p += 2; // Force on belt, N
            s.powerW = s16(bytes, p);
            p += 2;
        }

        return s;
    }

    function parseCrossTrainer(bytes) {
        var s = new FtmsSample(FtmsMachineKind.CROSS_TRAINER);

        if (!canRead(bytes, 0, 3)) { return s; }

        var flags = u24(bytes, 0);
        var p = 3;
        s.rawFlags = flags;

        // Bit 0 = More Data. If NOT set, instantaneous speed is present.
        if (!hasFlag(flags, 0)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.speedKmh = u16(bytes, p) / 100.0;
            p += 2;
        }

        // Bit 1: Average Speed, uint16, 0.01 km/h
        if (hasFlag(flags, 1)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 2: Total Distance, uint24, meters
        if (hasFlag(flags, 2)) {
            if (!canRead(bytes, p, 3)) { return s; }
            s.distanceM = u24(bytes, p).toFloat();
            p += 3;
        }

        // Bit 3: Step Rate + Average Step Rate, uint16 + uint16, steps/minute
        if (hasFlag(flags, 3)) {
            if (!canRead(bytes, p, 4)) { return s; }
            s.stepRateSpm = u16(bytes, p).toFloat();
            p += 2;
            p += 2; // Average step rate
        }

        // Bit 4: Stride Count, uint16, 0.1 stride count
        if (hasFlag(flags, 4)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.strideCount = u16(bytes, p) / 10.0;
            p += 2;
        }

        // Bit 5: Positive + Negative Elevation Gain, uint16 + uint16, meters
        if (hasFlag(flags, 5)) {
            if (!canRead(bytes, p, 4)) { return s; }
            s.positiveElevationM = u16(bytes, p).toFloat();
            p += 2;
            s.negativeElevationM = u16(bytes, p).toFloat();
            p += 2;
            s.elevationSource = 1;
        }

        // Bit 6: Inclination + Ramp Angle Setting, sint16, 0.1 units
        if (hasFlag(flags, 6)) {
            if (!canRead(bytes, p, 4)) { return s; }
            s.inclinePct = s16(bytes, p) / 10.0;
            p += 2;
            s.rampDeg = s16(bytes, p) / 10.0;
            p += 2;
        }

        // Bit 7: Resistance Level, sint16, 0.1 unitless
        if (hasFlag(flags, 7)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.resistance = s16(bytes, p) / 10.0;
            p += 2;
        }

        // Bit 8: Instantaneous Power, sint16, watts
        if (hasFlag(flags, 8)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.powerW = s16(bytes, p);
            p += 2;
        }

        // Bit 9: Average Power, sint16, watts
        if (hasFlag(flags, 9)) {
            if (!canRead(bytes, p, 2)) { return s; }
            p += 2;
        }

        // Bit 10: Expended Energy: total uint16 + per hour uint16 + per minute uint8
        if (hasFlag(flags, 10)) {
            if (!canRead(bytes, p, 5)) { return s; }
            p += 5;
        }

        // Bit 11: Heart Rate, uint8, bpm
        if (hasFlag(flags, 11)) {
            if (!canRead(bytes, p, 1)) { return s; }
            s.heartRateBpm = u8(bytes, p);
            p += 1;
        }

        // Bit 12: MET, uint8, 0.1 MET
        if (hasFlag(flags, 12)) {
            if (!canRead(bytes, p, 1)) { return s; }
            p += 1;
        }

        // Bit 13: Elapsed Time, uint16, seconds
        if (hasFlag(flags, 13)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.elapsedS = u16(bytes, p);
            p += 2;
        }

        // Bit 14: Remaining Time, uint16, seconds
        if (hasFlag(flags, 14)) {
            if (!canRead(bytes, p, 2)) { return s; }
            s.remainingS = u16(bytes, p);
            p += 2;
        }

        // Bit 15: Movement Direction. If set, backwards. If not set, forward/unspecified.
        if (hasFlag(flags, 15)) {
            s.movementDirection = 1;
        } else {
            s.movementDirection = 0;
        }

        return s;
    }

    function hasFlag(flags as Lang.Number, bit as Lang.Number) as Lang.Boolean {
        return (flags & (1 << bit)) != 0;
    }

    function canRead(bytes, pos as Lang.Number, len as Lang.Number) as Lang.Boolean {
        return bytes != null && bytes.size() >= pos + len;
    }

    function u8(bytes as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return bytes.decodeNumber(
            Lang.NUMBER_FORMAT_UINT8,
            {
                :offset => pos,
                :endianness => Lang.ENDIAN_LITTLE
            }
        );
    }

    function u16(bytes as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return bytes.decodeNumber(
            Lang.NUMBER_FORMAT_UINT16,
            {
                :offset => pos,
                :endianness => Lang.ENDIAN_LITTLE
            }
        );
    }

    function s16(bytes as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return bytes.decodeNumber(
            Lang.NUMBER_FORMAT_SINT16,
            {
                :offset => pos,
                :endianness => Lang.ENDIAN_LITTLE
            }
        );
    }

    function u24(bytes as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return u8(bytes, pos)
            | (u8(bytes, pos + 1) << 8)
            | (u8(bytes, pos + 2) << 16);
    }
}
