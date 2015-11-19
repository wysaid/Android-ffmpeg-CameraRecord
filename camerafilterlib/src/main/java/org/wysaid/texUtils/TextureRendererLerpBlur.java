package org.wysaid.texUtils;

import android.opengl.GLES20;
import android.util.Log;

import org.wysaid.myUtils.FrameBufferObject;
import org.wysaid.myUtils.ProgramObject;

/**
 * Created by wangyang on 15/7/24.
 */
public class TextureRendererLerpBlur extends TextureRendererDrawOrigin {

    private static final String vshScale = "" +
            "attribute vec2 vPosition;\n" +
            "varying vec2 texCoord;\n" +
            "void main()\n" +
            "{\n" +
            "   gl_Position = vec4(vPosition, 0.0, 1.0);\n" +
            "   texCoord = vPosition / 2.0 + 0.5;\n" +
            "}";

    private static final String fshScale = "" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform sampler2D inputImageTexture;\n" +

            "void main()\n" +
            "{\n" +
            "   gl_FragColor = texture2D(inputImageTexture, texCoord);\n" +
            "}";

    private ProgramObject mScaleProgram;
    private int[] mTextureDownScale;

    private FrameBufferObject mFramebuffer;
    private Viewport mTexViewport;

    private int mIntensity = 0;


    private final int mLevel = 16;
    private final float mBase = 2.0f;

    public static TextureRendererLerpBlur create(boolean isExternalOES) {
        TextureRendererLerpBlur renderer = new TextureRendererLerpBlur();
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
        if(mIntensity > mLevel)
            mIntensity = mLevel;
    }

    @Override
    public boolean init(boolean isExternalOES) {
        return super.init(isExternalOES) && initLocal();
    }

    @Override
    public void renderTexture(int texID, Viewport viewport) {

        if(mIntensity == 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            super.renderTexture(texID, viewport);
            return;
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        mFramebuffer.bindTexture(mTextureDownScale[0]);

        //down scale

        mTexViewport.width = calcMips(512, 1);
        mTexViewport.height = calcMips(512, 1);
        super.renderTexture(texID, mTexViewport);

        mScaleProgram.bind();
        for(int i = 1; i < mIntensity; ++i) {
            mFramebuffer.bindTexture(mTextureDownScale[i]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[i - 1]);
            GLES20.glViewport(0, 0, calcMips(512, i + 1), calcMips(512, i + 1));
            GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
        }

        //up scale

        for(int i = mIntensity - 1; i > 0; --i) {
            mFramebuffer.bindTexture(mTextureDownScale[i - 1]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[i]);
            GLES20.glViewport(0, 0, calcMips(512, i), calcMips(512, i));
            GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
        }

        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[0]);

        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
    }

    @Override
    public void release() {
        mScaleProgram.release();
        mFramebuffer.release();
        GLES20.glDeleteTextures(mTextureDownScale.length, mTextureDownScale, 0);
        mScaleProgram = null;
        mFramebuffer = null;
    }

    private boolean initLocal() {

        genMipmaps(mLevel, 512, 512);
        mFramebuffer = new FrameBufferObject();

        mScaleProgram = new ProgramObject();
        mScaleProgram.bindAttribLocation(POSITION_NAME, 0);

        if(!mScaleProgram.init(vshScale, fshScale)) {
            Log.e(LOG_TAG, "Lerp blur initLocal failed...");
            return false;
        }

        return true;
    }


    @Override
    public void setTextureSize(int w, int h) {
        super.setTextureSize(w, h);
    }

    private void genMipmaps(int level, int width, int height) {
        mTextureDownScale = new int[level];
        GLES20.glGenTextures(level, mTextureDownScale, 0);

        for(int i = 0; i < level; ++i) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDownScale[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, calcMips(width, i + 1), calcMips(height, i + 1), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        mTexViewport = new Viewport(0, 0, 512, 512);
    }

    private int calcMips(int len, int level) {
//        return (int)(len / Math.pow(mBase, (level + 1)));
        return len / (level + 1);
    }

}
