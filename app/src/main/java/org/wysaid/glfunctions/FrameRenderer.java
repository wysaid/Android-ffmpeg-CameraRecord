package org.wysaid.glfunctions;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

/**
 * Created by wangyang on 15/7/18.
 */
public class FrameRenderer {

    private static final String vshDraw = "" +
            "attribute vec2 vPosition;\n" +
            "varying vec2 texCoord;\n" +
            "uniform mat2 rotation;" +
            "void main()\n" +
            "{\n" +
            "   gl_Position = vec4(vPosition, 0.0, 1.0);\n" +
            "   texCoord = (vec2(vPosition.x, -vPosition.y) / 2.0) * rotation + 0.5;\n" +
            "}";

    private static final String fshBlur = "" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "   gl_FragColor = vec4(texCoord, 0.0, 1.0);//texture2D(inputImageTexture, texCoord);\n" +
            "}";

    private static final String fshBlur_ExternalOES = "" +
            "#extension GL_OES_EGL_image_external : require\n"+
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "   gl_FragColor = texture2D(inputImageTexture, texCoord);\n" +
            "}";

    private static final String fshWave_ExternalOES = "" +
            "#extension GL_OES_EGL_image_external : require\n"+
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "uniform float motion;\n" +
            "const float angle = 20.0;" +
            "void main()\n" +
            "{\n" +
            "   vec2 coord;\n" +
            "   coord.x = texCoord.x + 0.01 * sin(motion + texCoord.x * angle);\n" +
            "   coord.y = texCoord.y + 0.01 * sin(motion + texCoord.y * angle);\n" +
            "   gl_FragColor = texture2D(inputImageTexture, coord);\n" +
            "}";

    private static final String POSITION_NAME = "vPosition";
    private static final String ROTATION_NAME = "rotation";

    public static final float[] vertices = {-1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f};
    public static final int DRAW_FUNCTION = GLES20.GL_TRIANGLE_FAN;

    private int mVertexBuffer;
    private ProgramObject mProgram;
    private ProgramObject mProgramExt;

    public float[] mRotation;

    private int mMotionLoc = 0;

    public FrameRenderer() {
        int[] vertexBuffer = new int[1];
        GLES20.glGenBuffers(1, vertexBuffer, 0);
        mVertexBuffer = vertexBuffer[0];

        assert mVertexBuffer != 0 : "Invalid VertexBuffer!";

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer);
        FloatBuffer buffer = FloatBuffer.allocate(vertices.length);
        buffer.put(vertices);
        buffer.position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, buffer, GLES20.GL_STATIC_DRAW);

        mProgram = new ProgramObject();
        mProgram.bindAttribLocation(POSITION_NAME, 0);
        mProgram.init(vshDraw, fshBlur);

        mProgramExt = new ProgramObject();
        mProgramExt.bindAttribLocation(POSITION_NAME, 0);
//        mProgramExt.init(vshDraw, fshBlur_ExternalOES);
        mProgramExt.init(vshDraw, fshWave_ExternalOES);
        mProgramExt.bind();
        mMotionLoc = mProgramExt.getUniformLoc("motion");

        setRotation(0.0f);
    }

    public void setWaveMotion(float motion) {
        mProgramExt.bind();
        GLES20.glUniform1f(mMotionLoc, motion);
    }

    void release() {
        GLES20.glDeleteBuffers(1, new int[]{mVertexBuffer}, 0);
        mVertexBuffer = 0;
        mProgram.release();
        mProgramExt.release();
        mProgram = null;
        mProgramExt = null;
        mRotation = null;
    }

    public void renderTexture(int texID) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texID);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

        mProgram.bind();
        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
    }

    public void renderTextureExternalOES(int texID) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texID);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

        mProgramExt.bind();
        GLES20.glDrawArrays(DRAW_FUNCTION, 0, 4);
    }

    //设置界面旋转弧度,一般是 PI / 2 (也就是 90°) 的整数倍
    public void setRotation(float rad) {
        final float cosRad = (float)Math.cos(rad);
        final float sinRad = (float)Math.sin(rad);

        mRotation = new float[] {
            cosRad, sinRad,
            -sinRad, cosRad
        };

        mProgram.bind();
        mProgram.sendUniformMat2(ROTATION_NAME, 1, false, mRotation);

        mProgramExt.bind();
        mProgram.sendUniformMat2(ROTATION_NAME, 1, false, mRotation);
    }


}
