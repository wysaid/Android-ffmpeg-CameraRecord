package org.wysaid.framerenderer;

import android.opengl.GLES20;
import android.util.Log;

import org.wysaid.glfunctions.Common;
import org.wysaid.glfunctions.FrameBufferObject;

/**
 * Created by wangyang on 15/7/23.
 */
public class FrameRendererBlur extends FrameRendererDrawOrigin {

    private static final String vshBlur = vshDrawDefault;

    private static final String fshBlur = "" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "uniform vec2 samplerSteps;\n" +
            "uniform int samplerRadius;\n" +

            "float random(vec2 seed)\n" +
            "{\n" +
            "  return fract(sin(dot(seed ,vec2(12.9898,78.233))) * 43758.5453);\n" +
            "}\n" +

            "void main()\n" +
            "{\n" +
            "  vec4 resultColor = vec4(0.0);\n" +
            "  float blurPixels = 0.0;\n" +
            "  float offset = random(texCoord) - 0.5;\n" +
            "  \n" +
            "  for(int i = -samplerRadius; i <= samplerRadius; ++i)\n" +
            "  {\n" +
            "    float percent = (float(i) + offset) / float(samplerRadius);\n" +
            "    float weight = 1.0 - abs(percent);\n" +
            "    vec2 coord = texCoord + samplerSteps * percent;\n" +
            "    resultColor += texture2D(inputImageTexture, coord) * weight;\n" +
            "    blurPixels += weight;\n" +
            "  }\n" +

            "  gl_FragColor = resultColor / blurPixels;\n" +
            "}";

    protected int mTexCache = 0;

    protected FrameBufferObject mFBO;

    protected int mCacheTexWidth, mCacheTexHeight;

    private static final String SAMPLER_STEPS = "samplerSteps";
    private static final String SAMPLER_RADIUS = "samplerRadius";

    private int mStepsLoc = 0;
    private int mRadiusLoc = 0;

    private static final int RADIUS_LIMIT = 5;

    private float mSamplerScale = 1.0f;

    public static FrameRendererBlur create(boolean isExternalOES) {
        FrameRendererBlur renderer = new FrameRendererBlur();
        if(!renderer.init(isExternalOES)) {
            renderer.release();
            return null;
        }
        Common.checkGLError("FrameRendererBlur.renderxxxxxx");
        return renderer;
    }

    public void setSamplerRadius(float radius) {
        Common.checkGLError("FrameRendererBlur.renderdgjkuykyukuy");
        mProgram.bind();
        if(radius > RADIUS_LIMIT){
            mSamplerScale = radius / RADIUS_LIMIT;
            GLES20.glUniform1i(mRadiusLoc, RADIUS_LIMIT);
        }
        else {
            mSamplerScale = 1.0f;
            GLES20.glUniform1i(mRadiusLoc, (int)radius);
        }
        Common.checkGLError("FrameRendererBlur.renderdasdsadsa");
    }


    @Override
    public boolean init(boolean isExternalOES) {
        mFBO = new FrameBufferObject();
        if(setProgramDefualt(getVertexShaderString(), getFragmentShaderString(), isExternalOES)) {
            mProgram.bind();
            mStepsLoc = mProgram.getUniformLoc(SAMPLER_STEPS);
            mRadiusLoc = mProgram.getUniformLoc(SAMPLER_RADIUS);
            Common.checkGLError("FrameRendererBlur.init");
            return true;
        }
        return false;
    }

    @Override
    public void release() {
        super.release();
        mFBO.release();
        mFBO = null;
        GLES20.glDeleteTextures(1, new int[]{mTexCache}, 0);
        mTexCache = 0;
    }

    @Override
    public void renderTexture(int texID) {

//        if(mTexCache == 0 || mCacheTexWidth != mTextureWidth || mCacheTexHeight != mTextureHeight) {
//            resetCacheTexture();
//        }

//        mFBO.bind();

        Common.checkGLError("FrameRendererBlur.render0000");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(TEXTURE_2D_BINDABLE, texID);

        Common.checkGLError("FrameRendererBlur.render1111");
        mProgram.bind();
        GLES20.glUniform2f(mStepsLoc, (1.0f / mTextureWidth) * mSamplerScale, 0.0f);

        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
        Common.checkGLError("FrameRendererBlur.render");

//        GLES20.glFinish();
//
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexCache);
//        GLES20.glUniform2f(mStepsLoc, 0.0f, 1.0f / mTextureHeight * mSamplerScale);
//        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
    }

    @Override
    public void setTextureSize(int w, int h) {
        super.setTextureSize(w, h);
    }

    @Override
    public String getVertexShaderString() {
        return vshBlur;
    }

    @Override
    public String getFragmentShaderString() {
        return fshBlur;
    }


    protected void resetCacheTexture() {
        mCacheTexWidth = mTextureWidth;
        mCacheTexHeight = mTextureHeight;
        if(mTexCache == 0)
        {
            int[] texCache = new int[1];
            GLES20.glGenTextures(1, texCache, 0);
            mTexCache = texCache[0];
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexCache);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mCacheTexWidth, mCacheTexHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        mFBO.bindTexture(mTexCache);
    }

}
