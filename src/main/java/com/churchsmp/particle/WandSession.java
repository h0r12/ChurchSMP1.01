package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class WandSession {

    private final UUID playerUuid;
    private int currentFrame;
    private ParticleAlias activeParticle;
    private MathPreset activePreset;
    private double activeXOffset;
    private double activeYOffset;
    private double activeZOffset;
    private double activeVelX;
    private double activeVelY;
    private double activeVelZ;
    private double activeScale;
    private EasingType activeEasing;
    private int activeSpeed;

    private ParticleAnimation animation;
    private final Deque<ParticleAnimation> undoStack;
    private final Deque<ParticleAnimation> redoStack;

    private BukkitTask loopTask;

    private double activeRotX;
    private double activeRotY;
    private double activeRotZ;

    private int activeLifespan;
    private boolean decayShrink;
    private double activeSpinX;
    private double activeSpinY;
    private double activeSpinZ;

    private boolean recording;
    private BukkitTask recordTask;

    public WandSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.currentFrame = 0;
        this.activeParticle = ParticleAlias.fromString("dustzen");
        this.activePreset = MathPreset.STAR_4;
        this.activeXOffset = 0.0;
        this.activeYOffset = 0.0;
        this.activeZOffset = 0.0;
        this.activeVelX = 0.0;
        this.activeVelY = 0.0;
        this.activeVelZ = 0.0;
        this.activeScale = 1.0;
        this.activeEasing = EasingType.EASE_OUT_CUBIC;
        this.activeSpeed = 10;
        this.activeRotX = 0.0;
        this.activeRotY = 0.0;
        this.activeRotZ = 0.0;
        this.activeLifespan = 1;
        this.decayShrink = true;
        this.activeSpinX = 0.0;
        this.activeSpinY = 0.0;
        this.activeSpinZ = 0.0;
        this.recording = false;
        this.recordTask = null;

        this.animation = new ParticleAnimation("anim_" + playerUuid.toString().substring(0, 6));
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
    }

    public double getActiveRotX() {
        return activeRotX;
    }

    public void setActiveRotX(double activeRotX) {
        this.activeRotX = activeRotX;
    }

    public double getActiveRotY() {
        return activeRotY;
    }

    public void setActiveRotY(double activeRotY) {
        this.activeRotY = activeRotY;
    }

    public double getActiveRotZ() {
        return activeRotZ;
    }

    public void setActiveRotZ(double activeRotZ) {
        this.activeRotZ = activeRotZ;
    }

    public void setRotation(double rx, double ry, double rz) {
        this.activeRotX = rx;
        this.activeRotY = ry;
        this.activeRotZ = rz;
    }

    public int getActiveLifespan() {
        return activeLifespan;
    }

    public void setActiveLifespan(int activeLifespan) {
        this.activeLifespan = Math.max(1, activeLifespan);
    }

    public boolean isDecayShrink() {
        return decayShrink;
    }

    public void setDecayShrink(boolean decayShrink) {
        this.decayShrink = decayShrink;
    }

    public double getActiveSpinX() {
        return activeSpinX;
    }

    public void setActiveSpinX(double activeSpinX) {
        this.activeSpinX = activeSpinX;
    }

    public double getActiveSpinY() {
        return activeSpinY;
    }

    public void setActiveSpinY(double activeSpinY) {
        this.activeSpinY = activeSpinY;
    }

    public double getActiveSpinZ() {
        return activeSpinZ;
    }

    public void setActiveSpinZ(double activeSpinZ) {
        this.activeSpinZ = activeSpinZ;
    }

    public void setSpin(double sx, double sy, double sz) {
        this.activeSpinX = sx;
        this.activeSpinY = sy;
        this.activeSpinZ = sz;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(int currentFrame) {
        this.currentFrame = Math.max(0, currentFrame);
    }

    public void nextFrame() {
        this.currentFrame++;
    }

    public void prevFrame() {
        if (this.currentFrame > 0) {
            this.currentFrame--;
        }
    }

    public ParticleAlias getActiveParticle() {
        return activeParticle;
    }

    public void setActiveParticle(ParticleAlias activeParticle) {
        if (activeParticle != null) {
            this.activeParticle = activeParticle;
        }
    }

    public MathPreset getActivePreset() {
        return activePreset;
    }

    public void setActivePreset(MathPreset activePreset) {
        if (activePreset != null) {
            this.activePreset = activePreset;
        }
    }

    public double getActiveXOffset() {
        return activeXOffset;
    }

    public void setActiveXOffset(double activeXOffset) {
        this.activeXOffset = activeXOffset;
    }

    public double getActiveYOffset() {
        return activeYOffset;
    }

    public void setActiveYOffset(double activeYOffset) {
        this.activeYOffset = activeYOffset;
    }

    public double getActiveZOffset() {
        return activeZOffset;
    }

    public void setActiveZOffset(double activeZOffset) {
        this.activeZOffset = activeZOffset;
    }

    public void setCoordinates(double x, double y, double z) {
        this.activeXOffset = x;
        this.activeYOffset = y;
        this.activeZOffset = z;
    }

    public double getActiveVelX() {
        return activeVelX;
    }

    public void setActiveVelX(double activeVelX) {
        this.activeVelX = activeVelX;
    }

    public double getActiveVelY() {
        return activeVelY;
    }

    public void setActiveVelY(double activeVelY) {
        this.activeVelY = activeVelY;
    }

    public double getActiveVelZ() {
        return activeVelZ;
    }

    public void setActiveVelZ(double activeVelZ) {
        this.activeVelZ = activeVelZ;
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.activeVelX = vx;
        this.activeVelY = vy;
        this.activeVelZ = vz;
    }

    public double getActiveScale() {
        return activeScale;
    }

    public void setActiveScale(double activeScale) {
        this.activeScale = Math.max(0.1, activeScale);
    }

    public EasingType getActiveEasing() {
        return activeEasing;
    }

    public void setActiveEasing(EasingType activeEasing) {
        if (activeEasing != null) {
            this.activeEasing = activeEasing;
        }
    }

    public int getActiveSpeed() {
        return activeSpeed;
    }

    public void setActiveSpeed(int activeSpeed) {
        this.activeSpeed = Math.max(1, activeSpeed);
    }

    public ParticleAnimation getAnimation() {
        return animation;
    }

    public void setAnimation(ParticleAnimation animation) {
        if (animation != null) {
            saveSnapshot();
            this.animation = animation;
        }
    }

    public BukkitTask getLoopTask() {
        return loopTask;
    }

    public void setLoopTask(BukkitTask loopTask) {
        if (this.loopTask != null) {
            this.loopTask.cancel();
        }
        this.loopTask = loopTask;
    }

    public boolean isLooping() {
        return loopTask != null && !loopTask.isCancelled();
    }

    public void stopLoop() {
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }
    }

    private void saveSnapshot() {
        undoStack.push(animation.deepClone());
        if (undoStack.size() > 50) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(animation.deepClone());
        this.animation = undoStack.pop();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(animation.deepClone());
        this.animation = redoStack.pop();
        return true;
    }

    /**
     * Stamps the active preset into the current frame (and persists for activeLifespan frames if > 1).
     */
    public int stampActivePreset(Location anchor, ChurchSMP plugin) {
        saveSnapshot();

        List<Vector> points = activePreset.generatePoints(activeScale);
        int totalFrames = Math.max(1, activeLifespan);
        int stampedCount = 0;

        for (int f = 0; f < totalFrames; f++) {
            int frameIdx = currentFrame + f;
            AnimationFrame frame = animation.getOrCreateFrame(frameIdx);

            double decayScale = (decayShrink && totalFrames > 1)
                    ? activeScale * Math.max(0.15, 1.0 - (0.85 * ((double) f / totalFrames)))
                    : activeScale;

            double curRotX = activeRotX + (activeSpinX * f);
            double curRotY = activeRotY + (activeSpinY * f);
            double curRotZ = activeRotZ + (activeSpinZ * f);

            double curX = activeXOffset + (activeVelX * f);
            double curY = activeYOffset + (activeVelY * f);
            double baseZ = (activePreset != MathPreset.SLASH && activePreset != MathPreset.BOLT && activePreset != MathPreset.BOLT_VEINS ? 2.0 : 0.0);
            double curZ = baseZ + activeZOffset + (activeVelZ * f);

            for (Vector pt : points) {
                Vector transformed = rotateVector(pt.clone(), curRotX, curRotY, curRotZ);
                transformed.setX(transformed.getX() + curX);
                transformed.setY(transformed.getY() + curY);
                transformed.setZ(transformed.getZ() + curZ);

                FrameElement elem = new FrameElement(activeParticle, transformed, decayScale, 1, 0.0, activeEasing);
                frame.addElement(elem);

                if (f == 0) {
                    // Instant visual feedback spawn for current frame
                    elem.spawn(anchor, plugin);
                }
                stampedCount++;
            }
        }

        return stampedCount;
    }

    public void clearAnimation() {
        saveSnapshot();
        animation.clear();
        currentFrame = 0;
    }

    public boolean clearCurrentFrame() {
        saveSnapshot();
        AnimationFrame frame = animation.getFrames().get(currentFrame);
        if (frame != null && !frame.getElements().isEmpty()) {
            frame.getElements().clear();
            return true;
        }
        return false;
    }

    public int copyFrame(int fromFrame) {
        AnimationFrame source = animation.getFrames().get(fromFrame);
        if (source == null || source.getElements().isEmpty()) {
            return 0;
        }
        saveSnapshot();
        AnimationFrame target = animation.getOrCreateFrame(currentFrame);
        for (FrameElement elem : source.getElements()) {
            FrameElement cloned = new FrameElement(elem.getAlias(), elem.getLocalOffset().clone(), elem.getScale(), elem.getCount(), elem.getSpeed(), elem.getEasing());
            target.addElement(cloned);
        }
        return source.getElements().size();
    }

    public int rotateCurrentFrame(double rx, double ry, double rz) {
        AnimationFrame frame = animation.getFrames().get(currentFrame);
        if (frame == null || frame.getElements().isEmpty()) return 0;
        saveSnapshot();
        java.util.List<FrameElement> oldElems = new java.util.ArrayList<>(frame.getElements());
        frame.getElements().clear();
        for (FrameElement elem : oldElems) {
            Vector rotVec = rotateVector(elem.getLocalOffset().clone(), rx, ry, rz);
            frame.addElement(new FrameElement(elem.getAlias(), rotVec, elem.getScale(), elem.getCount(), elem.getSpeed(), elem.getEasing()));
        }
        return frame.getElements().size();
    }

    public static Vector rotateVector(Vector v, double rotXDeg, double rotYDeg, double rotZDeg) {
        if (rotXDeg == 0.0 && rotYDeg == 0.0 && rotZDeg == 0.0) return v;

        double radX = Math.toRadians(rotXDeg);
        double radY = Math.toRadians(rotYDeg);
        double radZ = Math.toRadians(rotZDeg);

        double cosX = Math.cos(radX), sinX = Math.sin(radX);
        double cosY = Math.cos(radY), sinY = Math.sin(radY);
        double cosZ = Math.cos(radZ), sinZ = Math.sin(radZ);

        // 1. Rotate around X (Pitch)
        double y1 = v.getY() * cosX - v.getZ() * sinX;
        double z1 = v.getY() * sinX + v.getZ() * cosX;
        double x1 = v.getX();

        // 2. Rotate around Y (Yaw)
        double x2 = x1 * cosY + z1 * sinY;
        double z2 = -x1 * sinY + z1 * cosY;
        double y2 = y1;

        // 3. Rotate around Z (Roll)
        double x3 = x2 * cosZ - y2 * sinZ;
        double y3 = x2 * sinZ + y2 * cosZ;
        double z3 = z2;

        return new Vector(x3, y3, z3);
    }

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    public BukkitTask getRecordTask() {
        return recordTask;
    }

    public void setRecordTask(BukkitTask recordTask) {
        if (this.recordTask != null) {
            this.recordTask.cancel();
        }
        this.recordTask = recordTask;
    }

    public void stopRecording() {
        this.recording = false;
        if (this.recordTask != null) {
            this.recordTask.cancel();
            this.recordTask = null;
        }
    }

    public int tweenFrames(int fromFrame, int toFrame, EasingType easing) {
        if (toFrame <= fromFrame + 1) return 0;

        AnimationFrame start = animation.getFrames().get(fromFrame);
        AnimationFrame end = animation.getFrames().get(toFrame);
        if (start == null || end == null || start.getElements().isEmpty() || end.getElements().isEmpty()) {
            return 0;
        }

        saveSnapshot();
        int totalIntermediateFrames = toFrame - fromFrame - 1;
        int maxElems = Math.max(start.getElements().size(), end.getElements().size());
        EasingType ease = (easing != null) ? easing : activeEasing;

        for (int step = 1; step <= totalIntermediateFrames; step++) {
            double rawT = (double) step / (toFrame - fromFrame);
            double t = EasingType.evaluate(ease, rawT);

            int targetFrameIdx = fromFrame + step;
            AnimationFrame target = animation.getOrCreateFrame(targetFrameIdx);
            target.getElements().clear();

            for (int i = 0; i < maxElems; i++) {
                FrameElement a = (i < start.getElements().size()) ? start.getElements().get(i) : start.getElements().get(start.getElements().size() - 1);
                FrameElement b = (i < end.getElements().size()) ? end.getElements().get(i) : end.getElements().get(end.getElements().size() - 1);

                Vector posA = a.getLocalOffset();
                Vector posB = b.getLocalOffset();

                double ix = posA.getX() + ((posB.getX() - posA.getX()) * t);
                double iy = posA.getY() + ((posB.getY() - posA.getY()) * t);
                double iz = posA.getZ() + ((posB.getZ() - posA.getZ()) * t);
                Vector interpolatedPos = new Vector(ix, iy, iz);

                double iscale = a.getScale() + ((b.getScale() - a.getScale()) * t);
                ParticleAlias alias = (t < 0.5) ? a.getAlias() : b.getAlias();

                target.addElement(new FrameElement(alias, interpolatedPos, iscale, 1, 0.0, ease));
            }
        }

        return totalIntermediateFrames;
    }

    public void applyTemplate(AnimationTemplate template) {
        if (template == null) return;
        saveSnapshot();
        template.generateInto(animation, activeParticle, activeScale);
        currentFrame = 0;
    }

    public int findPreviousNonEmptyFrame(int currentFrameIdx) {
        int best = 0;
        for (int f : animation.getFrames().keySet()) {
            if (f < currentFrameIdx) {
                AnimationFrame af = animation.getFrames().get(f);
                if (af != null && !af.getElements().isEmpty()) {
                    best = Math.max(best, f);
                }
            }
        }
        return best;
    }

    public int smartAutoTween(int fromFrame, int toFrame) {
        if (toFrame <= fromFrame + 1) return 0;

        AnimationFrame start = animation.getFrames().get(fromFrame);
        if (start == null || start.getElements().isEmpty()) {
            return 0;
        }

        AnimationFrame end = animation.getFrames().get(toFrame);
        boolean endEmpty = (end == null || end.getElements().isEmpty());

        saveSnapshot();

        if (endEmpty) {
            // Automatically clone starting frame with active transforms and 3D coordinate displacement
            end = animation.getOrCreateFrame(toFrame);
            for (FrameElement elem : start.getElements()) {
                Vector transformed = rotateVector(elem.getLocalOffset().clone(), activeRotX, activeRotY, activeRotZ);
                transformed.setX(transformed.getX() + activeXOffset);
                transformed.setY(transformed.getY() + activeYOffset);
                transformed.setZ(transformed.getZ() + activeZOffset);
                double destScale = Math.max(0.1, elem.getScale() * activeScale);
                end.addElement(new FrameElement(activeParticle, transformed, destScale, elem.getCount(), elem.getSpeed(), activeEasing));
            }
        }

        return tweenFrames(fromFrame, toFrame, activeEasing);
    }

    public int translateCurrentFrame(double dx, double dy, double dz) {
        AnimationFrame frame = animation.getFrames().get(currentFrame);
        if (frame == null || frame.getElements().isEmpty()) return 0;
        saveSnapshot();
        java.util.List<FrameElement> oldElems = new java.util.ArrayList<>(frame.getElements());
        frame.getElements().clear();
        for (FrameElement elem : oldElems) {
            Vector moved = elem.getLocalOffset().clone().add(new Vector(dx, dy, dz));
            frame.addElement(new FrameElement(elem.getAlias(), moved, elem.getScale(), elem.getCount(), elem.getSpeed(), elem.getEasing()));
        }
        return frame.getElements().size();
    }

    public int translateAllFrames(double dx, double dy, double dz) {
        if (animation.getFrames().isEmpty()) return 0;
        saveSnapshot();
        int total = 0;
        Vector delta = new Vector(dx, dy, dz);
        for (AnimationFrame frame : animation.getFrames().values()) {
            java.util.List<FrameElement> oldElems = new java.util.ArrayList<>(frame.getElements());
            frame.getElements().clear();
            for (FrameElement elem : oldElems) {
                Vector moved = elem.getLocalOffset().clone().add(delta);
                frame.addElement(new FrameElement(elem.getAlias(), moved, elem.getScale(), elem.getCount(), elem.getSpeed(), elem.getEasing()));
                total++;
            }
        }
        return total;
    }

    /**
     * Propagates particles in the current frame forward across activeLifespan frames.
     */
    public int applyDecayToCurrentFrame() {
        return applyDecayToFrame(currentFrame, activeLifespan, decayShrink);
    }

    /**
     * Propagates particles in a specific frame forward across lifespan frames.
     */
    public int applyDecayToFrame(int frameIdx, int lifespan, boolean shrink) {
        AnimationFrame source = animation.getFrames().get(frameIdx);
        if (source == null || source.getElements().isEmpty()) return 0;
        if (lifespan <= 1) return 0;

        saveSnapshot();
        int added = 0;
        java.util.List<FrameElement> baseElems = new java.util.ArrayList<>(source.getElements());

        for (int f = 1; f < lifespan; f++) {
            int targetIdx = frameIdx + f;
            AnimationFrame target = animation.getOrCreateFrame(targetIdx);

            double decayScale = shrink
                    ? Math.max(0.15, 1.0 - (0.85 * ((double) f / lifespan)))
                    : 1.0;

            for (FrameElement elem : baseElems) {
                double newScale = elem.getScale() * decayScale;
                target.addElement(new FrameElement(elem.getAlias(), elem.getLocalOffset().clone(), newScale, elem.getCount(), elem.getSpeed(), elem.getEasing()));
                added++;
            }
        }
        return added;
    }

    /**
     * Propagates particles across ALL non-empty frames forward by activeLifespan frames.
     */
    public int applyDecayToAllFrames() {
        if (activeLifespan <= 1 || animation.getFrames().isEmpty()) return 0;
        saveSnapshot();

        java.util.List<Integer> existingFrames = new java.util.ArrayList<>();
        java.util.Map<Integer, java.util.List<FrameElement>> originalData = new java.util.HashMap<>();

        for (java.util.Map.Entry<Integer, AnimationFrame> entry : animation.getFrames().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getElements().isEmpty()) {
                existingFrames.add(entry.getKey());
                originalData.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue().getElements()));
            }
        }
        java.util.Collections.sort(existingFrames);

        int totalAdded = 0;
        for (int frameIdx : existingFrames) {
            java.util.List<FrameElement> baseElems = originalData.get(frameIdx);
            if (baseElems == null || baseElems.isEmpty()) continue;

            for (int f = 1; f < activeLifespan; f++) {
                int targetIdx = frameIdx + f;
                AnimationFrame target = animation.getOrCreateFrame(targetIdx);

                double decayScale = decayShrink
                        ? Math.max(0.15, 1.0 - (0.85 * ((double) f / activeLifespan)))
                        : 1.0;

                for (FrameElement elem : baseElems) {
                    double newScale = elem.getScale() * decayScale;
                    target.addElement(new FrameElement(elem.getAlias(), elem.getLocalOffset().clone(), newScale, elem.getCount(), elem.getSpeed(), elem.getEasing()));
                    totalAdded++;
                }
            }
        }
        return totalAdded;
    }
}
