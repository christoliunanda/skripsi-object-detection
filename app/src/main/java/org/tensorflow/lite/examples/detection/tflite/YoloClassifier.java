/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tflite;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Vector;

import org.tensorflow.lite.Interpreter;

import org.tensorflow.lite.examples.detection.GalleryFragment;
import org.tensorflow.lite.examples.detection.NavigationActivity;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.env.Utils;

import static org.tensorflow.lite.examples.detection.env.Utils.expit;

import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 * <p>
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class YoloClassifier implements Classifier {

    private static final Logger LOGGER = new Logger();

    //config yolo
    private static String modelLabelName;
    private static final int INPUT_SIZE = 416;
    private static int[] OUTPUT_WIDTH;// = new int[]{52,26, 13}; //YOLOv3 - tiny 26,13

    private static int[][] MASKS;//= new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};  //YOLOv3 Masks while tiny is {{0,1,2},{3,4,5}}
    private static int[] ANCHORS;// = new int[]{
            //10,13, 16,30, 33,23, 30,61, 62,45, 59,119, 116,90, 156,198, 373,326
    //};  //YOLOv4 Anchors {12, 16, 19, 36, 40, 28, 36, 75, 76, 55, 72, 146, 142, 110, 192, 243, 459, 401}
    //YOLOv3 Anchors {10,13, 16,30, 33,23, 30,61, 62,45, 59,119, 116,90, 156,198, 373,326}  //9 Anchor Box  3 for each scale
    //YOLOv3-tiny Anchors {10,14, 23,27, 37,58, 81,82, 135,169, 344,319}//6 Anchor Box 3 for each scale

    private static final int NUM_BOXES_PER_BLOCK = 3;

    // Number of threads in the java app
    private static final int NUM_THREADS = 4;


    private boolean isModelQuantized;

    // Config values.

    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    //private int[] intValues;

    private ByteBuffer imgData;

    private Interpreter tfLite;

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param isQuantized   Boolean representing model is quantized or not
     */
    public static Classifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final boolean isQuantized)
            throws IOException {
        final YoloClassifier d = new YoloClassifier();

        loadYOLOConfiguration(modelFilename);

        //String actualFilename = labelFilename.split("file:///android_asset/")[1];
        InputStream labelsInput = assetManager.open(modelLabelName);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            //LOGGER.w(line);
            d.labels.add(line);
        }
        br.close();

        try {
            Interpreter.Options options = (new Interpreter.Options());
            options.setNumThreads(NUM_THREADS);
            d.tfLite = new Interpreter(Utils.loadModelFile(assetManager, modelFilename), options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        d.imgData = ByteBuffer.allocateDirect(1 * d.INPUT_SIZE * d.INPUT_SIZE * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        //d.intValues = new int[d.INPUT_SIZE * d.INPUT_SIZE];

        return d;
    }

    public static void loadYOLOConfiguration(String modelName){
        if (modelName.contains("tiny")){
            OUTPUT_WIDTH = new int[]{26, 13};
            MASKS = new int[][]{{0, 1, 2}, {3, 4, 5}};
            ANCHORS = new int[]{
                    10,14, 23,27, 37,58, 81,82, 135,169, 344,319
            };
        }else{
            OUTPUT_WIDTH = new int[]{52,26, 13};
            MASKS = new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
            ANCHORS = new int[]{
                    10,13, 16,30, 33,23, 30,61, 62,45, 59,119, 116,90, 156,198, 373,326
            };
        }

        if(modelName.contains("obj")){
            modelLabelName="custom-obj.txt";
        }else{
            modelLabelName="coco.txt";
        }
    }

    @Override
    public void enableStatLogging(final boolean logStats) {
    }

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
    }

    public void setNumThreads(int num_threads) {
        if (tfLite != null) tfLite.setNumThreads(num_threads);
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {
        if (tfLite != null) tfLite.setUseNNAPI(isChecked);
    }

    @Override
    public float getObjThresh() {
        return GalleryFragment.MINIMUM_CONFIDENCE_TF_OD_API;
    }



    private YoloClassifier() {
    }

    //non maximum suppression
    protected ArrayList<Recognition> nms(ArrayList<Recognition> list) {
        ArrayList<Recognition> nmsList = new ArrayList<Recognition>();
        for (int k = 0; k < labels.size(); k++) {
            //1.find max confidence per class
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            50,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition lhs, final Recognition rhs) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                                }
                            });

            for (int i = 0; i < list.size(); ++i) {
                if (list.get(i).getDetectedClass() == k) {
                    pq.add(list.get(i));
                }
            }

            //2.do non maximum suppression
            while (pq.size() > 0) {
                //insert detection with max confidence
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsList.add(max);
                pq.clear();

                for (int j = 1; j < detections.length; j++) {
                    Recognition detection = detections[j];
                    RectF b = detection.getLocation();
                    if (box_iou(max.getLocation(), b) < mNmsThresh) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsList;
    }

    protected float mNmsThresh = 0.6f;

    protected float box_iou(RectF a, RectF b) {
        return box_intersection(a, b) / box_union(a, b);
    }

    protected float box_intersection(RectF a, RectF b) {
        float w = overlap((a.left + a.right) / 2, a.right - a.left,
                (b.left + b.right) / 2, b.right - b.left);
        float h = overlap((a.top + a.bottom) / 2, a.bottom - a.top,
                (b.top + b.bottom) / 2, b.bottom - b.top);
        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float box_union(RectF a, RectF b) {
        float i = box_intersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }

    protected float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = l1 > l2 ? l1 : l2;
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = r1 < r2 ? r1 : r2;
        return right - left;
    }

    protected static final int BATCH_SIZE = 1;
    protected static final int PIXEL_SIZE = 3;

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    protected ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
        return byteBuffer;
    }

    public ArrayList<Recognition> recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);

        Map<Integer, Object> outputMap = new HashMap<>();
        for (int i = 0; i < OUTPUT_WIDTH.length; i++) {
            LOGGER.i("Testing: "+Integer.toString(i));
            float[][][][][] out = new float[1][OUTPUT_WIDTH[i]][OUTPUT_WIDTH[i]][3][5 + labels.size()];
            outputMap.put(i, out);
        }

        Log.d("YoloV4Classifier", "mObjThresh: " + getObjThresh());

        Object[] inputArray = {byteBuffer};
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        ArrayList<Recognition> detections = new ArrayList<Recognition>();

        long counter = 0;
        for (int i = 0; i < OUTPUT_WIDTH.length; i++) {
            int gridWidth = OUTPUT_WIDTH[i];
            float[][][][][] out = (float[][][][][]) outputMap.get(i);

            Log.d("YoloClassifier", "out[" + i + "] detect start");
            for (int y = 0; y < gridWidth; ++y) {
                for (int x = 0; x < gridWidth; ++x) {
                    for (int b = 0; b < NUM_BOXES_PER_BLOCK; ++b) {

                        final float otherConfidence = out[0][y][x][b][4];
                        final float confidence = expit(out[0][y][x][b][4]);
                        int detectedClassIndex = -1;
                        float maxClassValue = 0;

                        //final float[] classes = new float[labels.size()];
                        ArrayList<Float> classesArrayList = new ArrayList<Float>();
                        for (int c = 0; c < labels.size(); ++c) {
                            //classes[c] = out[0][y][x][b][5 + c];
                            classesArrayList.add(out[0][y][x][b][5 + c]);
                        }

                        maxClassValue = Collections.max(classesArrayList);
                        detectedClassIndex = classesArrayList.indexOf(maxClassValue);

//                        for (int c = 0; c < labels.size(); ++c) {
//                            if (classes[c] > maxClassValue) {
//                                detectedClassIndex = c;
//                                maxClassValue = classes[c];
//                            }
//                        }

                        final float confidenceInClass = maxClassValue * otherConfidence;
                        if (confidenceInClass > getObjThresh()) {
                            final float xPos = (x + expit(out[0][y][x][b][0])) * (1.0f * INPUT_SIZE / gridWidth);
                            final float yPos = (y + expit(out[0][y][x][b][1])) * (1.0f * INPUT_SIZE / gridWidth);
                            float rawXpos = (x + expit(out[0][y][x][b][0]));
                            float rawYpos = (y + expit(out[0][y][x][b][1]));

                            float anchorsw = ANCHORS[2 * MASKS[i][b]];
                            float anchorsh = ANCHORS[2 * MASKS[i][b] + 1];

                            float rawW=(float) Math.exp(out[0][y][x][b][2]);
                            float rawH=(float) Math.exp(out[0][y][x][b][3]);

                            final float w = (float) (Math.exp(out[0][y][x][b][2]) * ANCHORS[2 * MASKS[i][b]]);
                            final float h = (float) (Math.exp(out[0][y][x][b][3]) * ANCHORS[2 * MASKS[i][b] + 1]);

                            //Math.max(0, xPos - w / 2),
                            // Math.max(0, yPos - h / 2),
                            // Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                            // Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                            final RectF rect = new RectF(
                                            (xPos - w / 2),
                                            (yPos - h / 2),
                                            (xPos + w / 2),
                                            (yPos + h / 2));

                            detections.add(new Recognition("" + counter, labels.get(detectedClassIndex),
                                    confidenceInClass, rect, detectedClassIndex));
                        }
                        counter++;
                    }
                }
            }
            Log.d("YoloClassifier", "out[" + i + "] detect end");
        }

        final ArrayList<Recognition> recognitions = nms(detections);

        return recognitions;
    }


}
