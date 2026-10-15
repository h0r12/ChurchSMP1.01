package com.churchsmp.particle;

import java.util.*;

public class ParticleAnimation {

    private String name;
    private final Map<Integer, AnimationFrame> frames;
    private boolean directional;

    public ParticleAnimation(String name) {
        this.name = name != null ? name : "unnamed_anim";
        this.frames = new TreeMap<>();
        this.directional = true;
    }

    public ParticleAnimation(String name, Map<Integer, AnimationFrame> frames, boolean directional) {
        this.name = name;
        this.frames = new TreeMap<>(frames);
        this.directional = directional;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, AnimationFrame> getFrames() {
        return frames;
    }

    public boolean isDirectional() {
        return directional;
    }

    public void setDirectional(boolean directional) {
        this.directional = directional;
    }

    public int getMaxFrameIndex() {
        if (frames.isEmpty()) return 0;
        return Collections.max(frames.keySet());
    }

    public AnimationFrame getOrCreateFrame(int index) {
        return frames.computeIfAbsent(index, AnimationFrame::new);
    }

    public void clear() {
        frames.clear();
    }

    public ParticleAnimation deepClone() {
        Map<Integer, AnimationFrame> clonedFrames = new TreeMap<>();
        for (Map.Entry<Integer, AnimationFrame> entry : frames.entrySet()) {
            clonedFrames.put(entry.getKey(), entry.getValue().cloneFrame());
        }
        return new ParticleAnimation(this.name, clonedFrames, this.directional);
    }

    /**
     * Serializes to a compact portable 1-line string:
     * PAT:NAME[DIR]{F0:dustzen:^0,^0,^1:1.0:1:LINEAR|F1:star_4:...}
     */
    public String toPortableString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PAT:").append(name);
        sb.append(directional ? "[DIR]" : "[WORLD]");
        sb.append("{");

        boolean first = true;
        for (Map.Entry<Integer, AnimationFrame> entry : frames.entrySet()) {
            int frameIdx = entry.getKey();
            for (FrameElement elem : entry.getValue().getElements()) {
                if (!first) sb.append("|");
                first = false;
                sb.append("F").append(frameIdx).append(":").append(elem.serialize());
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses a portable 1-line string into a ParticleAnimation.
     */
    public static ParticleAnimation fromPortableString(String input) {
        if (input == null || !input.startsWith("PAT:")) return null;

        try {
            int bracketStart = input.indexOf('{');
            int bracketEnd = input.lastIndexOf('}');
            if (bracketStart == -1 || bracketEnd == -1 || bracketEnd <= bracketStart) return null;

            String header = input.substring(4, bracketStart);
            boolean directional = !header.contains("[WORLD]");
            String animName = header.replace("[DIR]", "").replace("[WORLD]", "").trim();
            if (animName.isEmpty()) animName = "imported_anim";

            ParticleAnimation anim = new ParticleAnimation(animName);
            anim.setDirectional(directional);

            String body = input.substring(bracketStart + 1, bracketEnd);
            if (!body.isBlank()) {
                String[] entries = body.split("\\|");
                for (String entry : entries) {
                    if (entry.startsWith("F")) {
                        int colonIdx = entry.indexOf(':');
                        if (colonIdx > 1) {
                            int fNum = Integer.parseInt(entry.substring(1, colonIdx));
                            String elemData = entry.substring(colonIdx + 1);
                            FrameElement elem = FrameElement.deserialize(elemData);
                            if (elem != null) {
                                anim.getOrCreateFrame(fNum).addElement(elem);
                            }
                        }
                    }
                }
            }

            return anim;
        } catch (Exception e) {
            return null;
        }
    }
}
