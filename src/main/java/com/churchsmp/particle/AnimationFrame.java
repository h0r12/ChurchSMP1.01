package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class AnimationFrame {

    private final int frameIndex;
    private final List<FrameElement> elements;

    public AnimationFrame(int frameIndex) {
        this.frameIndex = frameIndex;
        this.elements = new ArrayList<>();
    }

    public AnimationFrame(int frameIndex, List<FrameElement> elements) {
        this.frameIndex = frameIndex;
        this.elements = elements != null ? new ArrayList<>(elements) : new ArrayList<>();
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public List<FrameElement> getElements() {
        return elements;
    }

    public void addElement(FrameElement element) {
        if (element != null) {
            elements.add(element);
        }
    }

    public void spawn(Location anchor, ChurchSMP plugin) {
        for (FrameElement elem : elements) {
            elem.spawn(anchor, plugin);
        }
    }

    public AnimationFrame cloneFrame() {
        return new AnimationFrame(frameIndex, new ArrayList<>(elements));
    }
}
