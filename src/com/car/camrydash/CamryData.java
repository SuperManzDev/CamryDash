package com.car.camrydash;

public class CamryData {
    public float rpm = 0;
    public float speedMph = 0;
    public float coolantF = 195;
    public float intakeTempF = 72;
    public float engineLoad = 0;
    public float throttlePos = 0;
    public float voltage = 14.1f;
    public float maf = 0;
    public float fuelPercent = 75;
    public String gear = "P";
    public boolean connected = false;
    public boolean isSimulation = true;
    public String statusText = "SIMULATION MODE";

    // 2012 Toyota Camry 2.5L U760E 6-speed transmission specs
    private static final float FINAL_DRIVE = 3.815f;
    private static final float TIRE_REVS_PER_MILE = 807.0f; // 215/55R17 stock tire
    private static final float[] GEAR_RATIOS = { 3.300f, 1.900f, 1.420f, 1.000f, 0.713f, 0.608f };

    public void update(float rpm, float speedMph, float coolantC, float intakeC, float load, float throttle, float volt, float maf) {
        this.rpm = Math.max(0, rpm);
        this.speedMph = Math.max(0, speedMph);
        this.coolantF = (coolantC * 9.0f / 5.0f) + 32.0f;
        if (intakeC > -40) {
            this.intakeTempF = (intakeC * 9.0f / 5.0f) + 32.0f;
        }
        this.engineLoad = Math.min(100, Math.max(0, load));
        this.throttlePos = Math.min(100, Math.max(0, throttle));
        if (volt > 0) this.voltage = volt;
        if (maf >= 0) this.maf = maf;
        this.gear = estimateGear(this.rpm, this.speedMph);
    }

    public void updateDirectF(float rpm, float speedMph, float coolantF, float intakeF, float load, float throttle, float volt, float maf, String gear) {
        this.rpm = Math.max(0, rpm);
        this.speedMph = Math.max(0, speedMph);
        this.coolantF = coolantF;
        this.intakeTempF = intakeF;
        this.engineLoad = Math.min(100, Math.max(0, load));
        this.throttlePos = Math.min(100, Math.max(0, throttle));
        this.voltage = volt;
        this.maf = maf;
        this.gear = (gear != null && !gear.isEmpty()) ? gear : estimateGear(this.rpm, this.speedMph);
    }

    private String estimateGear(float rpm, float mph) {
        if (mph < 1.5f) {
            return (rpm > 400) ? "1" : "P";
        }
        if (rpm < 400) return "N";

        float totalRatio = (rpm * 60.0f) / (mph * TIRE_REVS_PER_MILE);
        float transRatio = totalRatio / FINAL_DRIVE;

        int closestGear = 1;
        float minDiff = Float.MAX_VALUE;

        for (int i = 0; i < GEAR_RATIOS.length; i++) {
            float diff = Math.abs(transRatio - GEAR_RATIOS[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closestGear = i + 1;
            }
        }

        return String.valueOf(closestGear);
    }
}
