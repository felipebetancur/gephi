/*
Copyright 2008-2011 Gephi
Authors : Vojtech Bardiovsky <vojtech.bardiovsky@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.gephi.visualization.camera;

import java.awt.Dimension;
import org.gephi.lib.gleem.linalg.Mat3f;
import org.gephi.lib.gleem.linalg.Mat4f;
import org.gephi.lib.gleem.linalg.Rotf;
import org.gephi.lib.gleem.linalg.Vec2f;
import org.gephi.lib.gleem.linalg.Vec3f;
import org.gephi.lib.gleem.linalg.Vec4f;
import org.gephi.visualization.api.camera.Camera;

/**
 * Class representing a camera for two dimensions. Enables basic camera movement.
 *
 * @author Antonio Patriarca <antoniopatriarca@gmail.com>
 * @author Vojtech Bardiovsky <vojtech.bardiovsky@gmail.com>
 */
public class Camera2d extends AbstractCamera {

    private Vec2f up;
    private Vec2f position;
    private final Vec3f front;

    private Mat4f projectiveMatrix;
    private Mat4f modelviewMatrix;

    private float imageWidth, imageHeight, near, far;

    public Camera2d(int width, int height, float near, float far) {
        this.imageWidth = width;
        this.imageHeight = height;
        this.fovy = 1.0f;
        this.near = near;
        this.far = far;

        this.position = new Vec2f();
        this.up = new Vec2f(0.0f, 1.0f);
        this.front = Vec3f.NEG_Z_AXIS;
    }

    public Camera2d(Camera3d camera) {
        this.imageWidth = camera.imageWidth();
        this.imageHeight = camera.imageHeight();
        this.fovy = camera.fov();
        this.near = camera.near();
        this.far = camera.far();

        this.position = convertTo2d(camera.position());
        this.up = convertTo2d(camera.upVector());
        this.front = Vec3f.NEG_Z_AXIS;
    }

    @Override
    public Camera copy() {
        Camera2d camera = new Camera2d((int) imageWidth, (int) imageHeight, near, far);
        camera.fovy = this.fovy;
        camera.position = this.position;
        camera.up = this.up;
        return camera;
    }

    @Override
    public void setImageSize(Dimension size) {
        this.imageWidth = size.width;
        this.imageHeight = size.height;
        requireRecomputeMatrix();
    }

    private void translate(Vec3f v) {
        this.position.add(convertTo2d(v));
        requireRecomputeMatrix();
    }

    /**
     * There is only one rotation axis in 2D and it will be used instead.
     */
    @Override
    public void rotate(Vec3f axis, float angle) {
        Rotf rot = new Rotf(front, angle);
        this.up = convertTo2d(rot.rotateVector(upVector()));
        requireRecomputeMatrix();
    }

    /**
     * There is only one rotation axis in 2D and it will be used instead.
     */
    @Override
    public void rotate(Vec3f origin, Vec3f axis, float angle) {
        Rotf rot = new Rotf(front, angle);
        this.up = convertTo2d(rot.rotateVector(upVector()));

        Vec3f diff = new Vec3f(position.x(), position.y(), 0).minus(new Vec3f(origin.x(), origin.y(), origin.z()));
        this.position.add(convertTo2d(origin), convertTo2d(rot.rotateVector(diff)));
        requireRecomputeMatrix();
    }

    /**
     * Moves the camera above the center.
     */
    @Override
    public void lookAt(Vec3f center, Vec3f up) {
        this.up = convertTo2d(up);
        this.up.normalize();
        this.position = convertTo2d(center);
        requireRecomputeMatrix();
    }

    /**
     * One of the center or position vectors are redundant. The position vector
     * will be ignored and camera moved above the center.
     */
    @Override
    public void lookAt(Vec3f position, Vec3f center, Vec3f up) {
        lookAt(center, up);
    }

    @Override
    public void setClipPlanes(float near, float far) {
        this.near = near;
        this.far = far;
        requireRecomputeMatrix();
    }

    @Override
    public Vec3f frontVector() {
        return this.front;
    }

    @Override
    public Vec3f upVector() {
        return new Vec3f(up.x(), up.y(), 0);
    }

    @Override
    public Vec3f rightVector() {
        return frontVector().cross(upVector());
    }

    @Override
    public Vec3f position() {
        return new Vec3f(position.x(), position.y(), 5550);
    }

    @Override
    public Vec3f lookAtPoint() {
        return new Vec3f(position.x(), position.y(), 0);
    }

    @Override
    public float imageWidth() {
        return this.imageWidth;
    }

    @Override
    public float imageHeight() {
        return this.imageHeight;
    }

    @Override
    public float near() {
        return this.near;
    }

    @Override
    public float far() {
        return this.far;
    }

    @Override
    public float fov() {
        return this.fovy;
    }

    @Override
    public float projectedDistanceFrom(Vec3f point) {
        Vec3f pnt = point.copy();
        pnt.sub(this.position());
        return pnt.dot(this.frontVector());
    }

    /**
     * Returns the model-view matrix.
     */
    @Override
    public Mat4f viewMatrix() {
        // FIXME may have better implementation or even return type
        if (recomputeMatrix) {
            Vec3f right = rightVector();
            Mat4f mat = new Mat4f();
            mat.setRotation(right, upVector(), this.front.times(-1.0f));
            mat.transpose();
            mat.setTranslation(this.position().times(-1.0f));
            mat.set(3, 3, 1.0f);
            modelviewMatrix = mat;
        }
        return modelviewMatrix;
    }

    /**
     * Returns the projective matrix.
     */
    @Override
    public Mat4f projectiveMatrix() {
        // FIXME may have better implementation or even return type
        if (recomputeMatrix) {
            Mat4f mat = new Mat4f();
            float aspect = imageWidth / imageHeight;
            float f = (float) (1.0 / Math.tan(this.fovy / 2.0));
            mat.set(0, 0, f/aspect);
            mat.set(1, 1, f);
            mat.set(2, 2, (this.far + this.near)/(this.near - this.far));
            mat.set(2, 3, (2.0f * this.far * this.near)/(this.near - this.far));
            mat.set(3, 2, -1.0f);
            projectiveMatrix = mat;
        }
        return projectiveMatrix;
    }

    /**
     * Returns the given point as it will appear on the screen together with its
     * size on screen after transformation have been applied.
     * @return array of integers, where
     * [0,1] -> point coordinates on screen
     * [2]   -> size of the node
     */
    @Override
    public int[] projectPoint(float x, float y, float z, float size) {
        // FIXME may have better implementation
        int[] res = new int[3];
        Vec4f point = new Vec4f(x, y, z, 1.0f);
        Vec4f screenPoint = new Vec4f();
        Mat4f viewProjMatrix = projectiveMatrix().mul(viewMatrix());
        // multiply by modelview and projection matrices
        viewProjMatrix.xformVec(point, screenPoint);
        screenPoint.scale(1.0f/screenPoint.w());
        // to NDC
        // point.scale(1 / point.w());
        res[0] = (int) ((screenPoint.x() + 1.0f) * imageWidth / 2.0f);
        res[1] = (int) ((1.0f - screenPoint.y()) * imageHeight / 2.0f);
        res[2] = 5;
        return res;
    }

    /**
     * Returns a point from camera viewing plane corresponding to the 2D point
     * on screen.
     */
    @Override
    public Vec3f projectPointInverse(float x, float y) {
        return new Vec3f(position.x(), position.y(), 0);
    }

    /**
     * Returns a vector from camera viewing plane corresponding to the 2D vector
     * on screen.
     */
    @Override
    public Vec3f projectVectorInverse(float x, float y) {
        float ratio = (float) Math.sqrt((1 - Math.cos(fovy)) / (1 - Math.cos(1.0)));
        Vec3f horizontalTranslation = this.rightVector().times(x * ratio);
        Vec3f verticalTranslation = this.upVector().times(y * ratio);
        Vec3f translation = new Vec3f();
        translation.add(horizontalTranslation, verticalTranslation);
        return translation;
    }

    @Override
    public int getPlanarDistance(float x, float y, float z, int a, int b) {
        int [] point = projectPoint(x, y, z, 0);
        return (int) Math.sqrt((point[0] - a) * (point[0] - a) + (point[1] - b) * (point[1] - b));
    }

    @Override
    public void startTranslation() {}

    @Override
    public void updateTranslation(float horizontal, float vertical) {
        float ratio = (float) Math.sqrt((1 - Math.cos(fovy)) / (1 - Math.cos(1.0)));
        Vec3f horizontalTranslation = this.rightVector().times(horizontal * ratio);
        Vec3f verticalTranslation = this.upVector().times(vertical * ratio);
        Vec3f translation = new Vec3f();
        translation.add(horizontalTranslation, verticalTranslation);
        translate(translation);
    }

    @Override
    public void startOrbit(float orbitModifier) {}

    @Override
    public void updateOrbit(float x, float y) {
        // FIXME may have better implementation
        Mat3f rotationMatrix = new Mat3f();
        float sx = (float) Math.sin(x);
        float cx = (float) Math.cos(x);
        rotationMatrix.setCol(0, new Vec3f(cx, sx, 0));
        rotationMatrix.setCol(1, new Vec3f(-sx, cx, 0));
        rotationMatrix.setCol(2, new Vec3f(0, 0, 1));
        Vec3f u = new Vec3f(upVector());
        Vec3f ur = new Vec3f();

        rotationMatrix.xformVec(u, ur);
        this.up = convertTo2d(ur);
        requireRecomputeMatrix();
    }

}
