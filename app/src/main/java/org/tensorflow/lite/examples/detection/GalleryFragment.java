package org.tensorflow.lite.examples.detection;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.env.Utils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloClassifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class GalleryFragment extends Fragment {
    private static final Logger LOGGER = new Logger();

    private Classifier detector;

    public static final int TF_OD_API_INPUT_SIZE = 416;

    private static final boolean TF_OD_API_IS_QUANTIZED = false;

    private static String TF_OD_API_MODEL_FILE;  //yolo-obj_4000.tflite

    private static String TF_OD_API_LABELS_FILE;//custom-obj

    private Bitmap sourceBitmap;
    private Bitmap cropBitmap;

    private Button  detectButton, changeImage;
    private ImageView imageView;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private OverlayView trackingOverlay;

    // Minimum detection confidence to track a detection.
    private static final boolean MAINTAIN_ASPECT = false;
    private Integer sensorOrientation = 90;

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;

    private static final int GALLERY_REQUEST_CODE = 123 ;
    public static final int RESULT_OK = -1;

    private static final String FILE_NAME="setting.txt";



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main,container,false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        detectButton = getView().findViewById(R.id.detectButton);
        changeImage = getView().findViewById(R.id.changeImageButton);

        imageView = getView().findViewById(R.id.imageView);

        //Create Loading Dialog
        ProgressDialog dialog = new ProgressDialog(getActivity()); // this = YourActivity
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setTitle("Loading");
        dialog.setMessage("Detecting Image. Please wait...");
        dialog.setIndeterminate(true);
        dialog.setCanceledOnTouchOutside(false);

        //Read model and label configuration file
        readConfiguration();

        //Gallery Button
        changeImage.setOnClickListener(v->{
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,"Pick an image"), GALLERY_REQUEST_CODE);

        });

        //Detect Button
        detectButton.setOnClickListener(v -> {
            Handler handler = new Handler();
            dialog.show();
            new Thread(() -> {
                final List<Classifier.Recognition> results = detector.recognizeImage(cropBitmap);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(cropBitmap, results);
                        dialog.dismiss();
                    }
                });
            }).start();

        });

        this.sourceBitmap = Utils.getBitmapFromAsset(this.getActivity(), "dog.jpg");

        this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);

        this.imageView.setImageBitmap(cropBitmap);



        initBox();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri imageData = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageData);
                this.cropBitmap = Utils.processBitmap(bitmap, TF_OD_API_INPUT_SIZE);
                this.imageView.setImageBitmap(cropBitmap);

                Intent intent = new Intent(getActivity(),DetectImageFromCamera.class);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readConfiguration(){
        FileInputStream fis = null;
        ArrayList<String> fullText = new ArrayList<>();
        try{
            fis=getActivity().openFileInput(FILE_NAME);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);

            String text;

            while((text = br.readLine()) != null){
                fullText.add(text);
            }
            String modelName= fullText.get(0);

            TF_OD_API_MODEL_FILE= modelName;
            TF_OD_API_LABELS_FILE=""; //"file:///android_asset/"+labelName;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initBox() {
        previewHeight = TF_OD_API_INPUT_SIZE;
        previewWidth = TF_OD_API_INPUT_SIZE;
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        tracker = new MultiBoxTracker(this.getActivity());
        trackingOverlay = getView().findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> tracker.draw(canvas));

        tracker.setFrameConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation);

        try {
            LOGGER.i(TF_OD_API_MODEL_FILE);
            LOGGER.i(TF_OD_API_LABELS_FILE);
            detector =
                    YoloClassifier.create(
                            getActivity().getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            this.getContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            this.getActivity().finish();

        }
    }

    private void handleResult(Bitmap bitmap, List<Classifier.Recognition> results) {
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        final Paint paintText = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        paintText.setColor(Color.RED);

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                float resultConfidence = result.getConfidence()*100;
                resultConfidence = Math.round(resultConfidence*100.0f)/100.0f;
                canvas.drawRect(location, paint);
                canvas.drawText(result.getTitle()+": "+resultConfidence+"%",location.left,location.top,paintText);


//                cropToFrameTransform.mapRect(location);
//
//                result.setLocation(location);
//                mappedRecognitions.add(result);
            }
        }
//        tracker.trackResults(mappedRecognitions, new Random().nextInt());
//        trackingOverlay.postInvalidate();

        imageView.setImageBitmap(bitmap);
    }
}
