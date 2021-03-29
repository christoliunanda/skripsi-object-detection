//package org.tensorflow.lite.examples.detection;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//
//import android.app.ProgressDialog;
//import android.content.Intent;
//import android.content.res.AssetManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Matrix;
//import android.graphics.Paint;
//import android.graphics.RectF;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.Handler;
//import android.provider.MediaStore;
//import android.widget.Button;
//import android.widget.ImageView;
//import android.widget.Toast;
//
//import org.tensorflow.lite.examples.detection.customview.OverlayView;
//import org.tensorflow.lite.examples.detection.env.ImageUtils;
//import org.tensorflow.lite.examples.detection.env.Logger;
//import org.tensorflow.lite.examples.detection.env.Utils;
//import org.tensorflow.lite.examples.detection.tflite.Classifier;
//import org.tensorflow.lite.examples.detection.tflite.YoloClassifier;
//import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
//
//import java.io.IOException;
//import java.util.LinkedList;
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity {
//    //Configuration values for the YOLO Model
//    private static final Logger LOGGER = new Logger();
//
//    public static final int TF_OD_API_INPUT_SIZE = 416;
//
//    private static final boolean TF_OD_API_IS_QUANTIZED = false;
//
//    private static final String TF_OD_API_MODEL_FILE = "yolov3.tflite";  //yolo-obj_4000.tflite
//
//    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt";//custom-obj
//
//    // Minimum detection confidence to track a detection.
//    private static final boolean MAINTAIN_ASPECT = false;
//    private Integer sensorOrientation = 90;
//
//    private Classifier detector;
//
//    private Matrix frameToCropTransform;
//    private Matrix cropToFrameTransform;
//    private MultiBoxTracker tracker;
//    private OverlayView trackingOverlay;
//
//    protected int previewWidth = 0;
//    protected int previewHeight = 0;
//
//    private Bitmap sourceBitmap;
//    private Bitmap cropBitmap;
//
//    private Button cameraButton, detectButton, changeImage;
//    private ImageView imageView;
//
//    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
//    private static final int GALLERY_REQUEST_CODE = 123 ;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        cameraButton = findViewById(R.id.cameraButton);
//        detectButton = findViewById(R.id.detectButton);
//        changeImage = findViewById(R.id.changeImageButton);
//
//        imageView = findViewById(R.id.imageView);
//
//        //Create Loading Dialog
//        ProgressDialog dialog = new ProgressDialog(this); // this = YourActivity
//        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//        dialog.setTitle("Loading");
//        dialog.setMessage("Detecting Image. Please wait...");
//        dialog.setIndeterminate(true);
//        dialog.setCanceledOnTouchOutside(false);
//
//        cameraButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, DetectorActivity.class)));
//
//        changeImage.setOnClickListener(v->{
//            Intent intent = new Intent();
//            intent.setType("image/*");
//            intent.setAction(Intent.ACTION_GET_CONTENT);
//            startActivityForResult(Intent.createChooser(intent,"Pick an image"), GALLERY_REQUEST_CODE);
//
//        });
//
//        detectButton.setOnClickListener(v -> {
//            Handler handler = new Handler();
//            dialog.show();
//            new Thread(() -> {
//                final List<Classifier.Recognition> results = detector.recognizeImage(cropBitmap);
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        handleResult(cropBitmap, results);
//                        dialog.dismiss();
//                    }
//                });
//            }).start();
//
//        });
//        this.sourceBitmap = Utils.getBitmapFromAsset(MainActivity.this, "dog.jpg");
//
//        this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);
//
//        this.imageView.setImageBitmap(cropBitmap);
//
//        initBox();
//    }
//
//
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
//            Uri imageData = data.getData();
//            try {
//                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageData);
//                this.cropBitmap = Utils.processBitmap(bitmap, TF_OD_API_INPUT_SIZE);
//                this.imageView.setImageBitmap(cropBitmap);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//
//    private void initBox() {
//        previewHeight = TF_OD_API_INPUT_SIZE;
//        previewWidth = TF_OD_API_INPUT_SIZE;
//        frameToCropTransform =
//                ImageUtils.getTransformationMatrix(
//                        previewWidth, previewHeight,
//                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
//                        sensorOrientation, MAINTAIN_ASPECT);
//
//        cropToFrameTransform = new Matrix();
//        frameToCropTransform.invert(cropToFrameTransform);
//
//        tracker = new MultiBoxTracker(this);
//        trackingOverlay = findViewById(R.id.tracking_overlay);
//        trackingOverlay.addCallback(
//                canvas -> tracker.draw(canvas));
//
//        tracker.setFrameConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation);
//
//        try {
//            detector =
//                    YoloClassifier.create(
//                            getAssets(),
//                            TF_OD_API_MODEL_FILE,
//                            TF_OD_API_LABELS_FILE,
//                            TF_OD_API_IS_QUANTIZED);
//        } catch (final IOException e) {
//            e.printStackTrace();
//            LOGGER.e(e, "Exception initializing classifier!");
//            Toast toast =
//                    Toast.makeText(
//                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
//            toast.show();
//
//        }
//    }
//
//    private void handleResult(Bitmap bitmap, List<Classifier.Recognition> results) {
//        final Canvas canvas = new Canvas(bitmap);
//        final Paint paint = new Paint();
//        paint.setColor(Color.RED);
//        paint.setStyle(Paint.Style.STROKE);
//        paint.setStrokeWidth(2.0f);
//
//        final List<Classifier.Recognition> mappedRecognitions =
//                new LinkedList<Classifier.Recognition>();
//
//        for (final Classifier.Recognition result : results) {
//            final RectF location = result.getLocation();
//            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
//                canvas.drawRect(location, paint);
//                canvas.drawText(result.getTitle(),location.left,location.top,paint);
////                cropToFrameTransform.mapRect(location);
////
////                result.setLocation(location);
////                mappedRecognitions.add(result);
//            }
//        }
////        tracker.trackResults(mappedRecognitions, new Random().nextInt());
////        trackingOverlay.postInvalidate();
//        imageView.setImageBitmap(bitmap);
//    }
//}
