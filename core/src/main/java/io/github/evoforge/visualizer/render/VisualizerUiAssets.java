package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/** Owns the runtime-generated fonts used by the immediate-mode visualizer UI. */
public final class VisualizerUiAssets {

    private static final int BODY_PX = 22;
    private static final int TITLE_PX = 25;

    private final BitmapFont body;
    private final BitmapFont title;

    public VisualizerUiAssets() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("ui/ui-font.ttf"));
        try {
            body = generate(generator, BODY_PX);
            title = generate(generator, TITLE_PX);
        } finally {
            generator.dispose();
        }
    }

    public BitmapFont window() { return title; }
    public BitmapFont subtitle() { return body; }
    public BitmapFont list() { return body; }
    public BitmapFont largeSubtitle() { return title; }
    public BitmapFont largeList() { return body; }

    public void dispose() {
        title.dispose();
        body.dispose();
    }

    private static BitmapFont generate(
            FreeTypeFontGenerator generator,
            int pixelSize) {

        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = pixelSize;
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        parameter.kerning = true;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;

        BitmapFont font = generator.generateFont(parameter);
        font.setUseIntegerPositions(true);
        return font;
    }
}
