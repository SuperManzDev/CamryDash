package com.car.camrydash;

import android.os.Handler;
import android.os.Looper;

public class MockDataGenerator {

    public interface OnTelemetryUpdateListener {
        void onUpdate(CamryData data);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CamryData data = new CamryData();
    private OnTelemetryUpdateListener listener;
    private boolean isRunning = false;
    private float simTime = 0.0f; // seconds
    private float simulatedCoolant = 150.0f;

    private final LoopRunnable loopRunnable;

    private static class LoopRunnable implements Runnable {
        private final MockDataGenerator generator;

        LoopRunnable(MockDataGenerator gen) {
            this.generator = gen;
        }

        @Override
        public void run() {
            if (!generator.isRunning) return;

            generator.stepSimulation(0.05f); // 50ms tick (20 Hz)

            if (generator.listener != null) {
                generator.listener.onUpdate(generator.data);
            }

            generator.handler.postDelayed(this, 50);
        }
    }

    public MockDataGenerator(OnTelemetryUpdateListener listener) {
        this.listener = listener;
        this.data.isSimulation = true;
        this.data.statusText = "SIMULATION RUNNING";
        this.loopRunnable = new LoopRunnable(this);
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            handler.post(loopRunnable);
        }
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacks(loopRunnable);
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void stepSimulation(float dt) {
        simTime += dt;
        float cycle = simTime % 50.0f; // 50-second driving cycle

        float rpm = 750.0f;
        float speed = 0.0f;
        float throttle = 0.0f;
        float load = 16.0f;
        float maf = 3.2f;
        String gear = "P";

        // Warm up coolant slowly toward 195 F
        if (simulatedCoolant < 195.0f) {
            simulatedCoolant += dt * 0.4f;
        }

        if (cycle < 4.0f) {
            // Parked Idle
            gear = "P";
            rpm = 740.0f + (float) (Math.sin(simTime * 2.0f) * 15.0f);
            speed = 0.0f;
            throttle = 0.0f;
            load = 18.0f;
            maf = 3.4f;
        } else if (cycle < 6.0f) {
            // Shift to Drive
            gear = "1";
            rpm = 690.0f + (float) (Math.sin(simTime * 2.0f) * 12.0f);
            speed = 0.0f;
            throttle = 0.0f;
            load = 22.0f;
            maf = 3.8f;
        } else if (cycle < 12.0f) {
            // Accelerate in 1st gear (0 -> 18 mph)
            gear = "1";
            float progress = (cycle - 6.0f) / 6.0f; // 0 to 1
            throttle = 38.0f;
            load = 58.0f;
            speed = progress * 18.0f;
            rpm = 1000.0f + progress * 2600.0f; // 1000 to 3600
            maf = 12.0f + progress * 32.0f;
        } else if (cycle < 13.0f) {
            // Shift 1 -> 2
            gear = "2";
            throttle = 25.0f;
            load = 35.0f;
            speed = 18.5f;
            rpm = 2100.0f;
            maf = 16.0f;
        } else if (cycle < 18.0f) {
            // Accelerate in 2nd gear (18.5 -> 34 mph)
            gear = "2";
            float progress = (cycle - 13.0f) / 5.0f;
            throttle = 35.0f;
            load = 52.0f;
            speed = 18.5f + progress * 15.5f;
            rpm = 2100.0f + progress * 1700.0f; // 2100 to 3800
            maf = 18.0f + progress * 28.0f;
        } else if (cycle < 19.0f) {
            // Shift 2 -> 3
            gear = "3";
            throttle = 25.0f;
            load = 32.0f;
            speed = 34.5f;
            rpm = 2200.0f;
            maf = 15.0f;
        } else if (cycle < 25.0f) {
            // Accelerate in 3rd gear (34.5 -> 52 mph)
            gear = "3";
            float progress = (cycle - 19.0f) / 6.0f;
            throttle = 32.0f;
            load = 48.0f;
            speed = 34.5f + progress * 17.5f;
            rpm = 2200.0f + progress * 1500.0f; // 2200 to 3700
            maf = 20.0f + progress * 22.0f;
        } else if (cycle < 26.0f) {
            // Shift 3 -> 4
            gear = "4";
            throttle = 20.0f;
            load = 28.0f;
            speed = 52.5f;
            rpm = 2100.0f;
            maf = 14.0f;
        } else if (cycle < 32.0f) {
            // Accelerate in 4th -> 5th gear (52.5 -> 68 mph)
            gear = (cycle < 29.0f) ? "4" : "5";
            float progress = (cycle - 26.0f) / 6.0f;
            throttle = 28.0f;
            load = 40.0f;
            speed = 52.5f + progress * 15.5f;
            rpm = 2100.0f + progress * 1100.0f; // 2100 to 3200
            maf = 18.0f + progress * 16.0f;
        } else if (cycle < 40.0f) {
            // 6th gear highway cruise (68 - 72 mph)
            gear = "6";
            float cruiseOsc = (float) Math.sin(simTime * 0.8f);
            speed = 70.0f + cruiseOsc * 2.0f;
            rpm = 1950.0f + cruiseOsc * 60.0f;
            throttle = 15.0f + cruiseOsc * 3.0f;
            load = 26.0f + cruiseOsc * 4.0f;
            maf = 14.5f + cruiseOsc * 2.0f;
        } else if (cycle < 47.0f) {
            // Decelerating / Braking to a stop
            float progress = (cycle - 40.0f) / 7.0f; // 0 to 1
            throttle = 0.0f;
            load = 12.0f;
            speed = Math.max(0.0f, 70.0f * (1.0f - progress));

            if (speed > 48.0f) {
                gear = "5";
                rpm = 1500.0f + (speed - 48.0f) * 20.0f;
            } else if (speed > 32.0f) {
                gear = "4";
                rpm = 1400.0f + (speed - 32.0f) * 25.0f;
            } else if (speed > 18.0f) {
                gear = "3";
                rpm = 1300.0f + (speed - 18.0f) * 30.0f;
            } else if (speed > 5.0f) {
                gear = "2";
                rpm = 1100.0f + (speed - 5.0f) * 35.0f;
            } else {
                gear = "1";
                rpm = 750.0f + speed * 40.0f;
            }
            maf = 4.0f + (speed / 70.0f) * 8.0f;
        } else {
            // Stopped at traffic light
            gear = "D";
            speed = 0.0f;
            rpm = 680.0f + (float) (Math.sin(simTime * 3.0f) * 10.0f);
            throttle = 0.0f;
            load = 20.0f;
            maf = 3.6f;
        }

        // Slight micro-jitter for analog realism
        float jitterRpm = rpm + ((float) Math.random() * 8.0f - 4.0f);
        float jitterVolt = 14.15f + ((float) Math.sin(simTime * 1.5f) * 0.08f);
        float intakeF = 74.0f + ((float) Math.sin(simTime * 0.1f) * 2.0f);

        data.updateDirectF(
            jitterRpm,
            speed,
            simulatedCoolant,
            intakeF,
            load,
            throttle,
            jitterVolt,
            maf,
            gear
        );
        data.connected = true;
        data.isSimulation = true;
        data.statusText = "SIMULATION (TEST MODE)";
    }
}
