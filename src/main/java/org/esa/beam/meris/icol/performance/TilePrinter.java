package org.esa.beam.meris.icol.performance;

import org.esa.beam.framework.gpf.internal.OperatorImage;
import org.esa.beam.framework.gpf.monitor.TileComputationEvent;
import org.esa.beam.framework.gpf.monitor.TileComputationObserver;

import java.util.HashSet;
import java.util.Set;

public class TilePrinter extends TileComputationObserver {

    private static class TileEvent {
        private final OperatorImage image;
        private final int tileX;
        private final int tileY;
        private final double duration;

        TileEvent(TileComputationEvent event) {
            this.image = event.getImage();
            this.tileX = event.getTileX();
            this.tileY = event.getTileY();
            this.duration = nanosToRoundedMillis((event.getEndNanos() - event.getStartNanos()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TileEvent that = (TileEvent) o;

            if (tileX != that.tileX) {
                return false;
            }
            if (tileY != that.tileY) {
                return false;
            }
            if (image != that.image) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = image.hashCode();
            result = 31 * result + tileX;
            result = 31 * result + tileY;
            return result;
        }


        private static double nanosToRoundedMillis(long nanos) {
            double ms = nanos * 1.0E-6;
            return Math.round(1000.0 * ms) / 1000.0;
        }
    }

    private final Set<TileEvent> recordedEventSet = new HashSet<TileEvent>();
    private int counter = 0;

    @Override
    public void start() {
        System.out.println("Num\tX\tY\tTW\tTH\tTime\tNew\tImg\n");
    }

    @Override
    public void tileComputed(TileComputationEvent event) {
        TileEvent tileEvent = new TileEvent(event);
        boolean newEvent = false;
        synchronized (recordedEventSet) {
            if (!recordedEventSet.contains(tileEvent)) {
                recordedEventSet.add(tileEvent);
                newEvent = true;
            }
        }

        counter++;
        System.out.printf("%d\t%d\t%d\t%d\t%d\t%f\t%s\t%s\n",
                          counter,
                          tileEvent.tileX,
                          tileEvent.tileY,
                          tileEvent.image.getTileWidth(),
                          tileEvent.image.getTileHeight(),
                          tileEvent.duration,
                          newEvent,
                          tileEvent.image);
    }

    @Override
    public void stop() {
    }
}
