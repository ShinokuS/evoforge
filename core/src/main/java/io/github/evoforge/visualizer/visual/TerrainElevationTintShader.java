package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * SpriteBatch shader for height-aware terrain coloring that preserves texture detail.
 *
 * <p>The vertex color red channel carries a normalized palette position from
 * {@link TerrainElevationColorRamp#position(long, long, long, int)}. A midpoint value of 0.5
 * leaves the source terrain untouched, lower values deepen the green, and higher values blend
 * toward a brighter grass tone. Ordinary callers encode that position as grayscale. Preview
 * adapters may additionally encode submerged darkening by raising the blue channel above red;
 * grayscale callers remain unaffected.</p>
 */
public final class TerrainElevationTintShader implements Disposable {
    private static final String VERTEX = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0 / 254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;

    private static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            void main() {
                vec4 texel = texture2D(u_texture, v_texCoords);
                float palette = clamp(v_color.r, 0.0, 1.0);
                float low = max(0.0, (0.5 - palette) * 2.0);
                float high = max(0.0, (palette - 0.5) * 2.0);

                vec3 darkened = texel.rgb * vec3(0.58, 0.72, 0.54);
                vec3 result = mix(texel.rgb, darkened, low * 0.82);

                // A restrained additive-looking lift makes high ground visibly lighter without
                // bleaching away the procedural grass texture.
                vec3 highTarget = vec3(0.76, 0.88, 0.55);
                result = mix(result, highTarget, high * 0.36);

                // Normal elevation colors are grayscale, so blue == red and this term is zero.
                // The generated-world preview can encode extra submerged depth in blue while
                // keeping red at the darkest accepted land palette position. Negative Z therefore
                // continues downward from land instead of resetting to a brighter neutral shade.
                float depthRange = max(0.0001, 1.0 - palette);
                float submerged = clamp((v_color.b - palette) / depthRange, 0.0, 1.0);
                result *= mix(1.0, 0.28, submerged);

                gl_FragColor = vec4(result, texel.a);
            }
            """;

    private final ShaderProgram shader;

    public TerrainElevationTintShader() {
        shader = new ShaderProgram(VERTEX, FRAGMENT);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("terrain elevation shader failed: " + log);
        }
    }

    public void apply(SpriteBatch batch) {
        if (batch == null) throw new IllegalArgumentException("batch must not be null");
        batch.setShader(shader);
    }

    public void clear(SpriteBatch batch) {
        if (batch == null) throw new IllegalArgumentException("batch must not be null");
        batch.setShader(null);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
