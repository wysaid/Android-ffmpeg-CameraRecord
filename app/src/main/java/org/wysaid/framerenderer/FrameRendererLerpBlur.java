package org.wysaid.framerenderer;

import android.opengl.GLES20;
import android.util.Log;

import org.wysaid.glfunctions.Common;
import org.wysaid.glfunctions.FrameBufferObject;
import org.wysaid.glfunctions.ProgramObject;

/**
 * Created by wangyang on 15/7/24.
 */
public class FrameRendererLerpBlur extends FrameRendererDrawOrigin{

    private static final String vshLerpBlur = "" +
            "attribute vec2 vPosition;\n" +
            "varying vec2 texCoord;\n" +
            "void main()\n" +
            "{\n" +
            "   gl_Position = vec4(vPosition, 0.0, 1.0);\n" +
            "   texCoord = vPosition / 2.0 + 0.5;\n" +
            "}";

//    private static final String fshUpScale = "" +
//            "precision mediump float;\n" +
//            "varying vec2 texCoord;\n" +
//            "uniform sampler2D inputImageTexture;\n" +
//
//            "void main()\n" +
//            "{\n" +
//            "   gl_FragColor = texture2D(inputImageTexture, texCoord);\n" +
//            "}";

    private static final String vshBlurUpScale = "" +
            "attribute vec2 vPosition;\n" +
            "varying vec2 texCoords[5];\n" +
            "uniform vec2 samplerSteps;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  gl_Position = vec4(vPosition, 0.0, 1.0);\n" +
            "  vec2 texCoord = vPosition / 2.0 + 0.5;\n" +
            "  texCoords[0] = texCoord - 2.0 * samplerSteps;\n" +
            "  texCoords[1] = texCoord - 1.0 * samplerSteps;\n" +
            "  texCoords[2] = texCoord;\n" +
            "  texCoords[3] = texCoord + 1.0 * samplerSteps;\n" +
            "  texCoords[4] = texCoord + 2.0 * samplerSteps;\n" +
            "}";

    private static final String fshBlurUpScale = "" +
            "precision mediump float;\n" +
            "varying vec2 texCoords[5];\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  vec3 color = texture2D(inputImageTexture, texCoords[0]).rgb * 0.1;\n" +
            "  color += texture2D(inputImageTexture, texCoords[1]).rgb * 0.2;\n" +
            "  color += texture2D(inputImageTexture, texCoords[2]).rgb * 0.4;\n" +
            "  color += texture2D(inputImageTexture, texCoords[3]).rgb * 0.2;\n" +
            "  color += texture2D(inputImageTexture, texCoords[4]).rgb * 0.1;\n" +
            "\n" +
            "  gl_FragColor = vec4(color, 1.0);\n" +
            "}";

    private static final String vshBlurCache = "" +
            "attribute vec2 vPosition;\n" +
            "varying vec2 texCoord;\n" +
            "void main()\n" +
            "{\n" +
            "   gl_Position = vec4(vPosition, 0.0, 1.0);\n" +
            "   texCoord = vPosition / 2.0 + 0.5;\n" +
            "}";

    private static final String fshBlur = "" +
            "precision highp float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform vec2 samplerSteps;\n" +

            "const int samplerRadius = 5;\n" +
            "const float samplerRadiusFloat = 5.0;\n" +

            "float random(vec2 seed)\n" +
            "{\n" +
            "  return fract(sin(dot(seed ,vec2(12.9898,78.233))) * 43758.5453);\n" +
            "}\n" +

            "void main()\n" +
            "{\n" +
            "  vec3 resultColor = vec3(0.0);\n" +
            "  float blurPixels = 0.0;\n" +
            "  float offset = random(texCoord) - 0.5;\n" +
            "  \n" +
            "  for(int i = -samplerRadius; i <= samplerRadius; ++i)\n" +
            "  {\n" +
            "    float percent = (float(i) + offset) / samplerRadiusFloat;\n" +
            "    float weight = 1.0 - abs(percent);\n" +
            "    vec2 coord = texCoord + samplerSteps * percent;\n" +
            "    resultColor += texture2D(inputImageTexture, coord).rgb * weight;\n" +
            "    blurPixels += weight;\n" +
            "  }\n" +

            "  gl_FragColor = vec4(resultColor / blurPixels, 1.0);\n" +
            "}";

    private static final String SAMPLER_STEPS = "samplerSteps";

    private ProgramObject mUpScaleProgram;
    private int[] mTextureDownScale;

    private FrameBufferObject mFramebuffer;
    private Viewport mTexViewport;
    private int mSamplerStepLoc = 0;

    private int mIntensity = 0;
    private boolean mShouldUpdateTexture = true;

    private float mSampleScaling = 1.0f;

    public static FrameRendererLerpBlur create(boolean isExternalOES) {
        FrameRendererLerpBlur renderer = new FrameRendererLerpBlur();
        if(!renderer.init(isExternalOES)) {
            renderer.release();
            return null;
        }
        return renderer;
    }

    //intensity >= 0
    public void setIntensity(int intensity) {

        if(intensity == mIntensity)
            return;

        mIntensity = intensity;
        mShouldUpdateTexture = true;
    }

    @Override
    public boolean init(boolean isExternalOES) {
        return super.init(isExternalOES) && initLocal();
    }

    @Override
    public void renderTexture(int texID, Viewport viewport) {

        if(mIntensity <= 1) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            super.renderTexture(texID, viewport);
            return;
        }

        if(mShouldUpdateTexture) {
            updateTexture();
        }

        mFramebuffer.bindTexture(mTextureDownScale[0]);
        //down scale
        super.renderTexture(texID, mTexViewport);

        mFramebuffer.bindTexture(mTextureDownScale[1]);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[0]);

        mUpScaleProgram.bind();
        GLES20.glUniform2f(mSamplerStepLoc, 0.5f / mTexViewport.height * mSampleScaling, 0.0f);
        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);

        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[1]);
        GLES20.glUniform2f(mSamplerStepLoc, 0.0f, (0.5f / mTexViewport.width) * mSampleScaling);

        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
    }

    @Override
    public void release() {
        mUpScaleProgram.release();
        mFramebuffer.release();
        GLES20.glDeleteTextures(2, mTextureDownScale, 0);
        mUpScaleProgram = null;
        mFramebuffer = null;
    }

    private boolean initLocal() {

        mFramebuffer = new FrameBufferObject();
        mTextureDownScale = new int[2];
        GLES20.glGenTextures(2, mTextureDownScale, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[1]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        mUpScaleProgram = new ProgramObject();
        mUpScaleProgram.bindAttribLocation(POSITION_NAME, 0);

//        if(!mUpScaleProgram.init(vshBlurUpScale, fshBlurUpScale)) {
        if(!mUpScaleProgram.init(vshBlurCache, fshBlur)) {
            Log.e(LOG_TAG, "Lerp blur initLocal failed...");
            return false;
        }

        mUpScaleProgram.bind();
        mSamplerStepLoc = mUpScaleProgram.getUniformLoc(SAMPLER_STEPS);

        return true;
    }

    private void updateTexture() {
        if(mIntensity == 0)
            return;

        int useIntensity = mIntensity;

        if(useIntensity > 6) {
            mSampleScaling = useIntensity / 6.0f;
            useIntensity = 6;
        }

        int scalingWidth = mTextureHeight / useIntensity;
        int scalingHeight = mTextureWidth / useIntensity;

        if(scalingWidth == 0)
            scalingWidth = 1;
        if(scalingHeight == 0)
            scalingHeight = 1;

        mTexViewport = new Viewport(0, 0, scalingWidth, scalingHeight);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, scalingWidth, scalingHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, scalingWidth, scalingHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        mShouldUpdateTexture = false;

        Log.i(LOG_TAG, "Lerp blur - updateTexture");

        Common.checkGLError("Lerp blur - updateTexture");
    }

}
